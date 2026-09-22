// Cliente HTTP para hablar con el backend
const BASE_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
const CLAVE_TOKEN = 'alquiler.token';

export class ErrorApi extends Error {
  constructor(estado, mensaje, detalles) {
    super(mensaje);
    this.estado = estado;
    this.detalles = detalles;
  }

  /** Errores por campo: { correo: 'mensaje', ... } */
  get porCampo() {
    const resultado = {};
    (this.detalles || []).forEach((d) => {
      if (d.campo && !resultado[d.campo]) resultado[d.campo] = d.mensaje;
    });
    return resultado;
  }
}

// ---------------- Token ----------------
// "Recordarme" guarda el token en localStorage (sobrevive al cerrar el navegador);
// si no, en sessionStorage (se borra al cerrar la pestaña).
export const almacenToken = {
  obtener() {
    return localStorage.getItem(CLAVE_TOKEN) || sessionStorage.getItem(CLAVE_TOKEN);
  },
  guardar(token, recordarme) {
    this.borrar();
    (recordarme ? localStorage : sessionStorage).setItem(CLAVE_TOKEN, token);
  },
  borrar() {
    localStorage.removeItem(CLAVE_TOKEN);
    sessionStorage.removeItem(CLAVE_TOKEN);
  },
};

/** Convierte una ruta del backend (/uploads/...) en URL completa */
export function urlArchivo(ruta) {
  if (!ruta) return null;
  return /^https?:\/\//.test(ruta) ? ruta : `${BASE_URL}${ruta}`;
}

async function peticion(metodo, ruta, { cuerpo, query, formData } = {}) {
  const url = new URL(`${BASE_URL}/api${ruta}`, window.location.origin);
  Object.entries(query || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
  });

  const cabeceras = {};
  const token = almacenToken.obtener();
  if (token) cabeceras.Authorization = `Bearer ${token}`;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';

  let respuesta;
  try {
    respuesta = await fetch(url, {
      method: metodo,
      headers: cabeceras,
      body: formData || (cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined),
    });
  } catch {
    throw new ErrorApi(0, 'No se pudo conectar con el servidor. Revisa tu conexión');
  }

  if (respuesta.status === 204) return null;
  const datos = await respuesta.json().catch(() => ({}));

  if (!respuesta.ok) {
    // Token vencido o cerrado: avisamos a la app para que cierre la sesión
    if (respuesta.status === 401 && token) {
      window.dispatchEvent(new CustomEvent('sesion-expirada', { detail: datos.error }));
    }
    throw new ErrorApi(respuesta.status, datos.error || 'Ocurrió un error inesperado', datos.detalles);
  }
  return datos;
}

export const api = {
  get: (ruta, query) => peticion('GET', ruta, { query }),
  post: (ruta, cuerpo) => peticion('POST', ruta, { cuerpo }),
  put: (ruta, cuerpo) => peticion('PUT', ruta, { cuerpo }),
  patch: (ruta, cuerpo) => peticion('PATCH', ruta, { cuerpo }),
  delete: (ruta) => peticion('DELETE', ruta),
  subir: (ruta, formData) => peticion('POST', ruta, { formData }),
};
