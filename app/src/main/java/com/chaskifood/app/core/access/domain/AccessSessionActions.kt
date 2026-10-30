package com.chaskifood.app.core.access.domain

/** Vincula las asignaciones PENDING de la sesion [expectedUid]. No decide elegibilidad: el backend exige correo
 * verificado coincidente, UID y exclusividad, y rechaza conflictos o reconciliaciones que requieren revision
 * autorizada sin escribir nada. Lanza excepcion si la vinculacion no se completo.
 */
fun interface PendingAssignmentBinder { suspend fun bind(expectedUid: String) }

/** Cierre de sesion centralizado de la app; devuelve false si no pudo protegerse ante una muerte inmediata del proceso. */
fun interface SessionSignOut { suspend fun signOut(): Boolean }
