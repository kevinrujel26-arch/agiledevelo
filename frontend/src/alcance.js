// Alcance funcional de la entrega (debe coincidir con ALCANCE_HU del backend).
//   4 = Milestone 1: HU1 inicio, HU2 registro/login, HU3 catálogo, HU4 detalle (valor por defecto)
//   5 = suma HU5: panel de administración (registrar y publicar máquinas, categorías, bloqueos)
//   6 = suma HU6: reservar máquina por horas
// El código de HU5 y HU6 no se borra: solo queda apagado hasta subir VITE_ALCANCE_HU.
const valor = Number.parseInt(import.meta.env.VITE_ALCANCE_HU ?? '4', 10);

export const ALCANCE_HU = Number.isNaN(valor) ? 4 : Math.max(4, Math.min(6, valor));
export const HU5_ACTIVA = ALCANCE_HU >= 5;
export const HU6_ACTIVA = ALCANCE_HU >= 6;
