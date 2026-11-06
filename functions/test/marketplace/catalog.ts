export type StoreSeed = Readonly<{
  id: string; name: string; address: string; phone: string; latitude: number; longitude: number;
}>;
export type ProductSeed = Readonly<{
  id: string; category: string; name: string; description: string; price: number; prepTimeMinutes: number; image: string;
}>;
export type BusinessSeed = Readonly<{
  id: string; ownerEmail: string; ownerName: string; ownerPhone: string; businessName: string; ruc: string;
  managerUid: string; managerEmail: string; managerName: string; managerPhone: string; assignedStoreId: string;
  image: string; categories: ReadonlyArray<Readonly<{ id: string; name: string }>>;
  stores: ReadonlyArray<StoreSeed>; products: ReadonlyArray<ProductSeed>;
}>;

export const BUSINESSES: ReadonlyArray<BusinessSeed> = [
  {
    id: "market-owner-chicken", ownerEmail: "owner.chicken@chaski.test", ownerName: "Propietario Chicken",
    ownerPhone: "+51900000101", businessName: "Chicken House", ruc: "20600000101",
    managerUid: "market-manager-chicken-centro", managerEmail: "manager.chicken.centro@chaski.test",
    managerName: "Responsable Centro Chicken", managerPhone: "+51900000102", assignedStoreId: "chicken-centro",
    image: "chicken/stores/cover.jpg",
    categories: [
      { id: "chicken-pollos", name: "Pollos" }, { id: "chicken-combos", name: "Combos" },
      { id: "chicken-complementos", name: "Complementos" }, { id: "chicken-bebidas", name: "Bebidas" },
    ],
    stores: [
      { id: "chicken-centro", name: "Chicken House Centro", address: "Av. Garcilaso de la Vega 1337, Cercado de Lima", phone: "900100101", latitude: -12.0572, longitude: -77.0375 },
      { id: "chicken-norte", name: "Chicken House Norte", address: "Av. Carlos Izaguirre 978, Los Olivos", phone: "900100102", latitude: -11.9905, longitude: -77.0707 },
      { id: "chicken-sur", name: "Chicken House Sur", address: "Av. Caminos del Inca 1502, Surco", phone: "900100103", latitude: -12.1293, longitude: -76.9824 },
    ],
    products: [
      { id: "chicken-pollo-entero", category: "chicken-pollos", name: "Pollo entero", description: "Pollo a la brasa entero, dorado y jugoso.", price: 59.9, prepTimeMinutes: 30, image: "chicken/products/pollo-entero.jpg" },
      { id: "chicken-medio-pollo", category: "chicken-pollos", name: "Medio pollo", description: "Medio pollo a la brasa con el sabor de la casa.", price: 32.9, prepTimeMinutes: 25, image: "chicken/products/medio-pollo.jpg" },
      { id: "chicken-cuarto-pollo", category: "chicken-pollos", name: "Cuarto de pollo", description: "Cuarto de pollo a la brasa, ideal para una persona.", price: 18.9, prepTimeMinutes: 20, image: "chicken/products/cuarto-pollo.jpg" },
      { id: "chicken-combo-familiar", category: "chicken-combos", name: "Combo familiar", description: "Pollo entero, papas, ensalada y bebida familiar.", price: 79.9, prepTimeMinutes: 35, image: "chicken/products/combo-familiar.jpg" },
      { id: "chicken-combo-personal", category: "chicken-combos", name: "Combo personal", description: "Cuarto de pollo, papas, ensalada y bebida.", price: 24.9, prepTimeMinutes: 22, image: "chicken/products/combo-personal.jpg" },
      { id: "chicken-papas", category: "chicken-complementos", name: "Papas fritas", description: "Papas doradas y crocantes.", price: 9.9, prepTimeMinutes: 10, image: "chicken/products/papas-fritas.jpg" },
      { id: "chicken-ensalada", category: "chicken-complementos", name: "Ensalada fresca", description: "Lechuga, tomate y verduras frescas.", price: 8.9, prepTimeMinutes: 8, image: "chicken/products/ensalada.jpg" },
      { id: "chicken-tequenos", category: "chicken-complementos", name: "Tequeños", description: "Seis tequeños crocantes con salsa de la casa.", price: 14.9, prepTimeMinutes: 12, image: "chicken/products/tequenos.jpg" },
      { id: "chicken-gaseosa", category: "chicken-bebidas", name: "Gaseosa", description: "Bebida gaseosa personal bien fría.", price: 5.9, prepTimeMinutes: 3, image: "chicken/products/gaseosa.jpg" },
      { id: "chicken-limonada", category: "chicken-bebidas", name: "Limonada", description: "Limonada natural preparada al momento.", price: 7.9, prepTimeMinutes: 5, image: "chicken/products/limonada.jpg" },
    ],
  },
  {
    id: "market-owner-burger", ownerEmail: "owner.burger@chaski.test", ownerName: "Propietario Burger",
    ownerPhone: "+51900000103", businessName: "Burger Station", ruc: "20600000102",
    managerUid: "market-manager-burger-este", managerEmail: "manager.burger.este@chaski.test",
    managerName: "Responsable Este Burger", managerPhone: "+51900000104", assignedStoreId: "burger-este",
    image: "burger/stores/cover.jpg",
    categories: [
      { id: "burger-hamburguesas", name: "Hamburguesas" }, { id: "burger-combos", name: "Combos" },
      { id: "burger-acompanamientos", name: "Acompañamientos" }, { id: "burger-bebidas", name: "Bebidas" },
    ],
    stores: [
      { id: "burger-este", name: "Burger Station Este", address: "Av. La Molina 1167, La Molina", phone: "900200101", latitude: -12.0718, longitude: -76.9568 },
      { id: "burger-centro", name: "Burger Station Centro", address: "Jr. de la Unión 780, Cercado de Lima", phone: "900200102", latitude: -12.0478, longitude: -77.0342 },
      { id: "burger-miraflores", name: "Burger Station Miraflores", address: "Av. José Pardo 610, Miraflores", phone: "900200103", latitude: -12.1178, longitude: -77.0311 },
    ],
    products: [
      { id: "burger-clasica", category: "burger-hamburguesas", name: "Hamburguesa clásica", description: "Carne, queso, lechuga, tomate y salsa de la casa.", price: 18.9, prepTimeMinutes: 15, image: "burger/products/hamburguesa-clasica.jpg" },
      { id: "burger-doble", category: "burger-hamburguesas", name: "Hamburguesa doble", description: "Doble carne, doble queso y vegetales frescos.", price: 25.9, prepTimeMinutes: 18, image: "burger/products/hamburguesa-doble.jpg" },
      { id: "burger-bacon", category: "burger-hamburguesas", name: "Burger bacon", description: "Carne, queso, tocino crocante y salsa barbacoa.", price: 24.9, prepTimeMinutes: 18, image: "burger/products/hamburguesa-bacon.jpg" },
      { id: "burger-pollo", category: "burger-hamburguesas", name: "Burger de pollo", description: "Pollo crocante, lechuga y mayonesa especial.", price: 21.9, prepTimeMinutes: 17, image: "burger/products/hamburguesa-pollo.jpg" },
      { id: "burger-vegetariana", category: "burger-hamburguesas", name: "Burger vegetariana", description: "Hamburguesa vegetal, queso y vegetales frescos.", price: 20.9, prepTimeMinutes: 16, image: "burger/products/hamburguesa-vegetariana.jpg" },
      { id: "burger-combo-clasico", category: "burger-combos", name: "Combo clásico", description: "Hamburguesa clásica, papas y bebida.", price: 27.9, prepTimeMinutes: 20, image: "burger/products/combo-clasico.jpg" },
      { id: "burger-papas", category: "burger-acompanamientos", name: "Papas fritas", description: "Papas gruesas, doradas y crocantes.", price: 9.9, prepTimeMinutes: 10, image: "burger/products/papas-fritas.jpg" },
      { id: "burger-aros", category: "burger-acompanamientos", name: "Aros de cebolla", description: "Aros de cebolla crocantes con salsa cremosa.", price: 12.9, prepTimeMinutes: 12, image: "burger/products/aros-cebolla.jpg" },
      { id: "burger-nuggets", category: "burger-acompanamientos", name: "Nuggets", description: "Ocho nuggets de pollo con salsa barbacoa.", price: 15.9, prepTimeMinutes: 13, image: "burger/products/nuggets.jpg" },
      { id: "burger-milkshake", category: "burger-bebidas", name: "Milkshake", description: "Batido cremoso de vainilla y chocolate.", price: 13.9, prepTimeMinutes: 8, image: "burger/products/milkshake.jpg" },
    ],
  },
];
