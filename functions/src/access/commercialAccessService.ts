import { Auth } from "firebase-admin/auth";
import { DocumentData, FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { CommercialIdentity } from "../auth/verifyBuyerToken";
import { Assignment, assignmentFrom, assertExclusive, CommercialAccessError, CommercialCommand,
  confirmedAssignments, digest, normalizedEmail, ownsCommercialBusiness, validId } from "./commercialAccessPolicy";

export const accessIndex = "commercial_access_index";
type Subject = { uid: string | null; email: string; emailVerified: boolean };
type Context = {
  subject: Subject; business: DocumentData | undefined; rows: Assignment[];
  mail: DocumentData | undefined; user: DocumentData | undefined;
  revision: number; sourceHash: string;
};
const emailKey = (email: string) => `email_${digest(email)}`;
const uidKey = (uid: string) => `uid_${uid}`;
const rowHash = (rows: Assignment[]) => digest([...rows].sort((a, b) => a.id.localeCompare(b.id)));

/** Auth se consulta fuera de la transaccion; no se afirma atomicidad Auth/Firestore. */
export class CommercialAccessService {
  constructor(private readonly db: Firestore, private readonly auth: Auth) {}

  private async subject(email: string): Promise<Subject> {
    email = normalizedEmail(email);
    try {
      const user = await this.auth.getUserByEmail(email);
      if (user.disabled) throw new CommercialAccessError("IDENTITY_UNAVAILABLE", 403);
      return { uid: user.uid, email, emailVerified: user.emailVerified };
    } catch (error) {
      if ((error as { code?: string }).code === "auth/user-not-found") return { uid: null, email, emailVerified: false };
      throw error;
    }
  }

  private async context(tx: Transaction, subject: Subject, preparing = false): Promise<Context> {
    const mail = (await tx.get(this.db.collection(accessIndex).doc(emailKey(subject.email)))).data();
    const user = subject.uid ? (await tx.get(this.db.collection(accessIndex).doc(uidKey(subject.uid)))).data() : undefined;
    const business = subject.uid ? (await tx.get(this.db.doc(`business_requests/${subject.uid}`))).data() : undefined;
    const owners = subject.uid ? await tx.get(this.db.collection("business_requests").where("ownerUid", "==", subject.uid)) : null;
    if (owners?.docs.some(d => d.id !== subject.uid) || (business && business.ownerUid !== subject.uid) ||
        (business && !["PENDING_REVIEW", "APPROVED", "OBSERVED", "REJECTED", "SUSPENDED"].includes(business.status))) {
      throw new CommercialAccessError("DATA_INCONSISTENT", 503);
    }
    const snapshot = await tx.get(this.db.collection("store_managers").where("email", "==", subject.email));
    const rows = snapshot.docs.map(d => assignmentFrom(d.id, d.data()));
    let hasLiveBindings = false;
    let boundRows = 0;
    if (subject.uid) {
      const bound = await tx.get(this.db.collection("store_managers").where("boundUid", "==", subject.uid));
      boundRows = bound.size;
      hasLiveBindings = bound.docs.some(d => d.data().bindingState !== "REVOKED");
      if (bound.docs.some(d => d.data().bindingState !== "REVOKED" && d.data().email !== subject.email)) {
        throw new CommercialAccessError("RECONCILIATION_REQUIRED");
      }
    }
    if (user && (user.kind !== "UID" || user.uid !== subject.uid || (user.emailKey !== emailKey(subject.email) && (!preparing || hasLiveBindings)))) {
      throw new CommercialAccessError("RECONCILIATION_REQUIRED");
    }
    if (mail && (mail.kind !== "EMAIL" || (mail.boundUid && mail.boundUid !== subject.uid))) {
      throw new CommercialAccessError("BINDING_OWNED_BY_ANOTHER_UID");
    }
    const sourceHash = rowHash(rows);
    // Implicit CLIENT (D2-H1): no index and no commercial evidence at all, read inside this same transaction.
    // Nothing is written here; the first commercial operation creates the index at revision 1. Any trace of
    // ownership, assignment or partial index keeps requiring an authorized preparation.
    if (!preparing && !mail && !user && !business && !owners?.size && rows.length === 0 && boundRows === 0) {
      return { subject, business, rows, mail, user, revision: 0, sourceHash };
    }
    if (!preparing && (!mail || (subject.uid && !user))) throw new CommercialAccessError("PREPARATION_REQUIRED");
    if (!preparing && mail!.sourceHash !== sourceHash) throw new CommercialAccessError("INDEX_INCONSISTENT");
    if (mail && (!Number.isSafeInteger(mail.revision) || mail.revision < 0)) throw new CommercialAccessError("INDEX_INCONSISTENT");
    return { subject, business, rows, mail, user, revision: mail?.revision ?? 0, sourceHash };
  }

  private writeIndex(tx: Transaction, ctx: Context, rows: Assignment[], revision: number): void {
    tx.set(this.db.collection(accessIndex).doc(emailKey(ctx.subject.email)), {
      kind: "EMAIL", schemaVersion: 1, boundUid: ctx.mail?.boundUid ?? null,
      revision, sourceHash: rowHash(rows), updatedAt: FieldValue.serverTimestamp(),
    });
    if (ctx.subject.uid) tx.set(this.db.collection(accessIndex).doc(uidKey(ctx.subject.uid)), {
      kind: "UID", schemaVersion: 1, uid: ctx.subject.uid, emailKey: emailKey(ctx.subject.email), authEmail: ctx.subject.email,
      revision, updatedAt: FieldValue.serverTimestamp(),
    });
  }

  private async requireOwner(tx: Transaction, actor: CommercialIdentity, businessId: string): Promise<void> {
    const ctx = await this.context(tx, actor);
    if (businessId !== actor.uid || ctx.business?.ownerUid !== actor.uid || ctx.business.status !== "APPROVED") {
      throw new CommercialAccessError("FORBIDDEN", 403);
    }
    assertExclusive(ctx.business.status, ctx.rows, actor.uid);
    if (ctx.rows.some(r => r.bindingState === "LEGACY" || r.bindingState === "RECONCILIATION_REQUIRED")) {
      throw new CommercialAccessError("RECONCILIATION_REQUIRED");
    }
  }

  async execute(actor: CommercialIdentity, command: CommercialCommand) {
    if (["PREPARE", "APPROVE"].includes(command.action) && !actor.admin) throw new CommercialAccessError("FORBIDDEN", 403);
    const auditRef = this.db.doc(`audit_logs/access_${digest([actor.uid, command.operationId])}`);
    const requestHash = digest(command);
    // Recuperar exito previo no exige que la cuenta destinataria siga existiendo.
    const prior = (await auditRef.get()).data();
    if (prior) {
      if (prior.requestHash !== requestHash) throw new CommercialAccessError("OPERATION_ID_REUSED");
      return prior.result as { revision: number; action: string };
    }
    let target: Subject = actor;
    if (["PREPARE", "ASSIGN"].includes(command.action)) target = await this.subject(command.email!);
    if (command.action === "REVOKE") {
      const email = normalizedEmail(command.email);
      const mail = (await this.db.collection(accessIndex).doc(emailKey(email)).get()).data();
      target = { uid: mail?.boundUid ?? null, email, emailVerified: false };
    }
    if (command.action === "APPROVE") {
      const user = await this.auth.getUser(command.businessId!);
      if (!user.email || user.disabled) throw new CommercialAccessError("IDENTITY_UNAVAILABLE", 403);
      target = { uid: user.uid, email: normalizedEmail(user.email), emailVerified: user.emailVerified };
    }
    let previousSubject: Subject | undefined;
    if (command.previousEmail && normalizedEmail(command.previousEmail) !== target.email) {
      const email = normalizedEmail(command.previousEmail);
      const oldMail = (await this.db.collection(accessIndex).doc(emailKey(email)).get()).data();
      previousSubject = { uid: oldMail?.boundUid ?? null, email, emailVerified: false };
    }
    return this.db.runTransaction(async tx => {
      const receipt = (await tx.get(auditRef)).data();
      if (receipt) {
        if (receipt.requestHash !== requestHash) throw new CommercialAccessError("OPERATION_ID_REUSED");
        return receipt.result as { revision: number; action: string };
      }
      const ctx = await this.context(tx, target, command.action === "PREPARE");
      if (ctx.revision !== command.expectedRevision) throw new CommercialAccessError("STALE_REVISION");
      if (["ASSIGN", "REVOKE"].includes(command.action)) await this.requireOwner(tx, actor, command.businessId!);
      const previous = previousSubject ? await this.context(tx, previousSubject) : undefined;
      if (previous && previous.revision !== command.previousRevision) throw new CommercialAccessError("STALE_REVISION");
      const previousRow = previous?.rows.find(r => r.id === `${command.businessId}_${previous.subject.email}`);
      if (previous && (!previousRow || previousRow.bindingState === "REVOKED")) throw new CommercialAccessError("ASSIGNMENT_NOT_FOUND", 404);
      let rows = [...ctx.rows];
      let mutation: (() => void) | undefined;
      let bindingUid = ctx.mail?.boundUid ?? null;
      switch (command.action) {
        case "PREPARE":
          // Recalcula solo metadatos derivados; nunca vincula ni elimina registros antiguos.
          break;
        case "APPROVE": {
          if (!ctx.business || ctx.business.ownerUid !== target.uid || ctx.business.status !== "PENDING_REVIEW") throw new CommercialAccessError("BUSINESS_STATE_CHANGED");
          if (confirmedAssignments(rows, target.uid!).length) throw new CommercialAccessError("AUTHORIZATION_CONFLICT");
          if (rows.some(r => r.bindingState === "LEGACY" || r.bindingState === "RECONCILIATION_REQUIRED")) throw new CommercialAccessError("RECONCILIATION_REQUIRED");
          mutation = () => tx.update(this.db.doc(`business_requests/${target.uid}`), {
            status: "APPROVED", reviewedBy: actor.email, reviewedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(), lastAuditId: auditRef.id,
          });
          break;
        }
        case "ASSIGN": {
          if (ownsCommercialBusiness(ctx.business?.status)) throw new CommercialAccessError("AUTHORIZATION_CONFLICT");
          const id = `${command.businessId}_${target.email}`;
          const existing = rows.find(r => r.id === id);
          if (existing && existing.bindingState !== "REVOKED" && existing.boundUid && existing.boundUid !== target.uid) throw new CommercialAccessError("BINDING_OWNED_BY_ANOTHER_UID");
          for (const id of command.storeIds!) {
            const store = (await tx.get(this.db.doc(`stores/${id}`))).data();
            if (!store || store.businessId !== command.businessId) throw new CommercialAccessError("STORE_NOT_OWNED", 403);
          }
          // Reemplazar una vinculacion vieja exige primero revocacion autorizada, no transferencia.
          const bindingState = target.uid && target.emailVerified ? "VERIFIED" : "PENDING";
          const row: Assignment = { id, businessId: command.businessId!, email: target.email,
            assignedStoreIds: command.storeIds!, bindingState, boundUid: bindingState === "VERIFIED" ? target.uid : null };
          rows = rows.filter(r => r.id !== id).concat(row);
          if (row.boundUid) bindingUid = row.boundUid;
          mutation = () => tx.set(this.db.doc(`store_managers/${id}`), {
            businessId: row.businessId, email: row.email, assignedStoreIds: row.assignedStoreIds,
            bindingState: row.bindingState, boundUid: row.boundUid, fullName: command.fullName, phone: command.phone,
            ...(existing ? {} : { createdAt: Date.now() }), updatedAt: FieldValue.serverTimestamp(),
          }, { merge: true });
          break;
        }
        case "REVOKE": {
          const id = `${command.businessId}_${target.email}`;
          const row = rows.find(r => r.id === id);
          if (!row) throw new CommercialAccessError("ASSIGNMENT_NOT_FOUND", 404);
          rows = rows.map(r => r.id === id ? { ...r, assignedStoreIds: [], bindingState: "REVOKED", boundUid: null } : r);
          mutation = () => tx.update(this.db.doc(`store_managers/${id}`), {
            assignedStoreIds: [], bindingState: "REVOKED", boundUid: null, updatedAt: FieldValue.serverTimestamp(),
          });
          break;
        }
        case "BIND": {
          if (!actor.emailVerified) throw new CommercialAccessError("EMAIL_VERIFICATION_REQUIRED", 403);
          if (rows.some(r => r.bindingState === "LEGACY" || r.bindingState === "RECONCILIATION_REQUIRED")) throw new CommercialAccessError("RECONCILIATION_REQUIRED");
          if (rows.some(r => r.bindingState !== "REVOKED" && r.boundUid && r.boundUid !== actor.uid)) throw new CommercialAccessError("BINDING_OWNED_BY_ANOTHER_UID");
          if (ownsCommercialBusiness(ctx.business?.status) && rows.some(r => r.bindingState !== "REVOKED")) throw new CommercialAccessError("AUTHORIZATION_CONFLICT");
          rows = rows.map(r => r.bindingState === "PENDING" ? { ...r, bindingState: "VERIFIED", boundUid: actor.uid } : r);
          bindingUid = actor.uid;
          mutation = () => rows.filter(r => r.bindingState === "VERIFIED").forEach(r => tx.update(this.db.doc(`store_managers/${r.id}`), { bindingState: "VERIFIED", boundUid: actor.uid }));
          break;
        }
        case "REGISTER":
        case "RESUBMIT": {
          if (confirmedAssignments(rows, actor.uid).length) throw new CommercialAccessError("MANAGER_CANNOT_REGISTER", 403);
          if (rows.some(r => r.bindingState === "LEGACY" || r.bindingState === "PENDING" || r.bindingState === "RECONCILIATION_REQUIRED")) throw new CommercialAccessError("RECONCILIATION_REQUIRED");
          if (command.action === "REGISTER" ? !!ctx.business : !ctx.business || ctx.business.status !== "OBSERVED") throw new CommercialAccessError("BUSINESS_STATE_CHANGED");
          mutation = () => tx.set(this.db.doc(`business_requests/${actor.uid}`), {
            ...command.business, ownerUid: actor.uid, status: "PENDING_REVIEW", observations: null, suspensionReason: null,
            reviewedBy: null, reviewedAt: null, updatedAt: FieldValue.serverTimestamp(),
            ...(command.action === "REGISTER" ? { createdAt: FieldValue.serverTimestamp() } : {}),
          }, { merge: true });
          break;
        }
      }
      const revision = ctx.revision + 1;
      if (!Number.isSafeInteger(revision)) throw new CommercialAccessError("INDEX_INCONSISTENT", 503);
      // Cambio de correo: retirar fila anterior y crear nueva en la misma transaccion.
      if (previous && previousRow) {
        const oldRows = previous.rows.map(r => r.id === previousRow.id ? { ...r, assignedStoreIds: [], bindingState: "REVOKED" as const, boundUid: null } : r);
        tx.update(this.db.doc(`store_managers/${previousRow.id}`), { assignedStoreIds: [], bindingState: "REVOKED", boundUid: null, updatedAt: FieldValue.serverTimestamp() });
        this.writeIndex(tx, previous, oldRows, previous.revision + 1);
      }
      mutation?.();
      ctx.mail = { ...ctx.mail, boundUid: bindingUid };
      this.writeIndex(tx, ctx, rows, revision);
      const result = { action: command.action, revision };
      tx.create(auditRef, {
        adminUid: actor.uid, adminEmail: actor.email, action: command.action === "APPROVE" ? "APROBAR_NEGOCIO" : `ACCESS_${command.action}`,
        targetBusinessId: command.businessId ?? target.uid ?? "", targetBusinessName: ctx.business?.businessName ?? "",
        targetRuc: ctx.business?.ruc ?? "", details: "Coordinacion comercial", timestamp: FieldValue.serverTimestamp(),
        previousStatus: ctx.business?.status ?? null, newStatus: command.action === "APPROVE" ? "APPROVED" : ctx.business?.status ?? null,
        requestHash, result,
      });
      return result;
    });
  }

  /** Composable read-only authorization inside the caller's transaction, never a nested transaction. */
  async canStartPurchase(tx: Transaction, actor: CommercialIdentity): Promise<boolean> {
    const ctx = await this.context(tx, actor);
    if (ownsCommercialBusiness(ctx.business?.status) || confirmedAssignments(ctx.rows, actor.uid).length) return false;
    if (ctx.rows.some(r => !["REVOKED", "VERIFIED"].includes(r.bindingState))) {
      throw new CommercialAccessError("RECONCILIATION_REQUIRED", 503);
    }
    return true; // CLIENT from verified facts; admin claims never bypass these checks.
  }

  /** HU16 inbox: read-only, same HU34 context/index/exclusivity as existing facts.
   * Status/operational availability do not revoke a verified assignment. */
  async requireStoreOrderReader(tx: Transaction, actor: CommercialIdentity, storeId: string): Promise<{businessId:string}> {
    const assigned = (await this.orderReaderAssignments(tx, actor)).filter(row => row.assignedStoreIds.includes(storeId));
    if (assigned.length !== 1) throw new CommercialAccessError("FORBIDDEN",403);
    return {businessId:assigned[0].businessId};
  }

  /** Device registration eligibility only; it grants nothing and every send calls requireStoreOrderReader again. */
  async requireAnyStoreOrderReader(tx: Transaction, actor: CommercialIdentity): Promise<void> {
    if (!(await this.orderReaderAssignments(tx, actor)).length) throw new CommercialAccessError("FORBIDDEN",403);
  }

  private async orderReaderAssignments(tx: Transaction, actor: CommercialIdentity): Promise<Assignment[]> {
    const ctx = await this.context(tx, actor);
    assertExclusive(ctx.business?.status, ctx.rows, actor.uid);
    if (ownsCommercialBusiness(ctx.business?.status)) throw new CommercialAccessError("FORBIDDEN",403);
    if (!actor.emailVerified || ctx.mail?.boundUid !== actor.uid) throw new CommercialAccessError("FORBIDDEN",403);
    if (ctx.rows.some(row => !["VERIFIED","REVOKED"].includes(row.bindingState)))
      throw new CommercialAccessError("RECONCILIATION_REQUIRED",503);
    return confirmedAssignments(ctx.rows, actor.uid);
  }

  async hasReceivingManager(tx: Transaction, businessId: string, storeId: string): Promise<boolean> {
    const docs = await tx.get(this.db.collection("store_managers").where("businessId", "==", businessId));
    let uncertain = false;
    for (const doc of docs.docs) {
      const row = assignmentFrom(doc.id, doc.data());
      if (!row.assignedStoreIds.includes(storeId) || row.bindingState === "REVOKED") continue;
      if (!["PENDING", "VERIFIED"].includes(row.bindingState) || (row.bindingState === "PENDING" && row.boundUid)) {
        uncertain = true; continue;
      }
      try {
        // PENDING need not have an Auth account. Existing UID indexes resolve known identities without Auth I/O.
        const known = await tx.get(this.db.collection(accessIndex).where("kind", "==", "UID").where("authEmail", "==", row.email));
        if (known.size > 1) throw new CommercialAccessError("INDEX_INCONSISTENT", 503);
        const knownUid = known.docs[0]?.data().uid as string | undefined;
        const uid = row.boundUid ?? knownUid ?? null;
        const ctx = await this.context(tx, { uid, email: row.email, emailVerified: row.bindingState === "VERIFIED" });
        assertExclusive(ctx.business?.status, ctx.rows, uid ?? "");
        if (ownsCommercialBusiness(ctx.business?.status)) continue;
        if (row.bindingState === "VERIFIED" && (ctx.mail?.boundUid !== uid || (knownUid && knownUid !== uid))) {
          throw new CommercialAccessError("INDEX_INCONSISTENT", 503);
        }
        return true;
      } catch (error) {
        if (!(error instanceof CommercialAccessError)) throw error;
        uncertain = true;
      }
    }
    if (uncertain) throw new CommercialAccessError("RECONCILIATION_REQUIRED", 503);
    return false;
  }

  async assignmentRevision(actor: CommercialIdentity, businessId: string, email?: string) {
    if (!validId(businessId)) throw new CommercialAccessError("INVALID_REQUEST", 400);
    let target: Subject;
    if (email === undefined) {
      if (!actor.admin) throw new CommercialAccessError("FORBIDDEN", 403);
      const user = await this.auth.getUser(businessId);
      if (!user.email || user.disabled) throw new CommercialAccessError("IDENTITY_UNAVAILABLE", 403);
      target = { uid: user.uid, email: normalizedEmail(user.email), emailVerified: user.emailVerified };
    } else {
      const normalized = normalizedEmail(email);
      const mail = (await this.db.collection(accessIndex).doc(emailKey(normalized)).get()).data();
      target = mail?.boundUid ? { uid: mail.boundUid, email: normalized, emailVerified: false } : await this.subject(normalized);
    }
    return this.db.runTransaction(async tx => {
      if (email !== undefined) await this.requireOwner(tx, actor, businessId);
      const ctx = await this.context(tx, target);
      return { revision: ctx.revision };
    }, { readOnly: true });
  }

  async ownRevision(actor: CommercialIdentity) {
    return this.db.runTransaction(async tx => ({ revision: (await this.context(tx, actor)).revision }), { readOnly: true });
  }

  async facts(actor: CommercialIdentity) {
    return this.db.runTransaction(async tx => {
      const ctx = await this.context(tx, actor);
      assertExclusive(ctx.business?.status, ctx.rows, actor.uid);
      if (ctx.rows.some(r => r.bindingState === "LEGACY" || r.bindingState === "PENDING" || r.bindingState === "RECONCILIATION_REQUIRED")) {
        // Invitacion no efectiva no bloquea propiedad legitima; no se convierte en permiso.
        if (!ownsCommercialBusiness(ctx.business?.status) || ctx.rows.some(r => r.bindingState !== "PENDING" && r.bindingState !== "REVOKED" && r.bindingState !== "VERIFIED")) {
          throw new CommercialAccessError("RECONCILIATION_REQUIRED");
        }
      }
      const assignments = confirmedAssignments(ctx.rows, actor.uid);
      if (assignments.length && (!actor.emailVerified || ctx.mail?.boundUid !== actor.uid)) throw new CommercialAccessError("EMAIL_VERIFICATION_REQUIRED", 403);
      const stores = [];
      // Propiedad aprobada: la consulta y sus estados pertenecen a la misma instantanea.
      // Suspendido conserva identidad, pero no recibe listado de locales.
      if (ctx.business?.status === "APPROVED") {
        const owned = await tx.get(this.db.collection("stores").where("businessId", "==", actor.uid));
        for (const doc of owned.docs) {
          const store = doc.data();
          if (!["ACTIVE", "INACTIVE", "SUSPENDED"].includes(store.status) ||
              !["OPEN", "CLOSED", "PAUSED"].includes(store.operationalStatus)) throw new CommercialAccessError("DATA_INCONSISTENT", 503);
          stores.push({ storeId: doc.id, businessId: actor.uid, status: store.status,
            operationalStatus: store.operationalStatus, businessStatus: "APPROVED" });
        }
      }
      for (const row of assignments) for (const id of row.assignedStoreIds) {
        const store = (await tx.get(this.db.doc(`stores/${id}`))).data();
        const business = (await tx.get(this.db.doc(`business_requests/${row.businessId}`))).data();
        if (!store || store.businessId !== row.businessId || !business ||
            !["ACTIVE", "INACTIVE", "SUSPENDED"].includes(store.status) ||
            !["OPEN", "CLOSED", "PAUSED"].includes(store.operationalStatus) ||
            !["APPROVED", "SUSPENDED", "PENDING_REVIEW", "OBSERVED", "REJECTED"].includes(business.status)) throw new CommercialAccessError("DATA_INCONSISTENT", 503);
        stores.push({ storeId: id, businessId: row.businessId, status: store.status, operationalStatus: store.operationalStatus, businessStatus: business.status });
      }
      return { uid: actor.uid, revision: ctx.revision, exclusivity: "VERIFIED", ownership: ctx.business ? {
        businessId: actor.uid, ownerUid: ctx.business.ownerUid, status: ctx.business.status,
        suspensionReason: ctx.business.suspensionReason ?? null,
      } : null, assignments, stores };
    }, { readOnly: true });
  }
}
