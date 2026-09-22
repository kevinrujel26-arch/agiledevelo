export const NOMBRE_APP = 'MaquiRenta';

const moneda = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' });
export const formatearMoneda = (monto) => moneda.format(monto ?? 0);

/** '2026-10-05' -> '05/10/2026' (sin conversión de zona horaria) */
export function formatearFecha(fechaTexto) {
  if (!fechaTexto) return '';
  const [a, m, d] = fechaTexto.slice(0, 10).split('-');
  return `${d}/${m}/${a}`;
}

export function formatearRango(inicio, fin) {
  return inicio === fin ? formatearFecha(inicio) : `${formatearFecha(inicio)} – ${formatearFecha(fin)}`;
}

/** Fecha de hoy 'YYYY-MM-DD' según el navegador */
export function hoyISO() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export const ETIQUETA_ESTADO = {
  BORRADOR: 'Borrador',
  PUBLICADA: 'Publicada',
  RETIRADA: 'Retirada',
};

export const CORREO_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
