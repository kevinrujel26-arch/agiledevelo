// Reglas de validación de formularios. Son las mismas (y con los mismos mensajes) que aplica
// el backend en alquiler/util/Validador.java y los controladores: si cambias una, cambia la otra.
// Cada regla recibe el valor y devuelve el mensaje de error, o '' si es válido.

export const NOMBRE_MIN = 2;
export const NOMBRE_MAX = 120;
export const CORREO_MAX = 160;
export const CONTRASENA_MIN = 8;
export const CONTRASENA_MAX = 72;

export const CORREO_VALIDO = /^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
export const CELULAR_VALIDO = /^9\d{8}$/;
const CONTROL = /\p{Cc}/u;
const SALTOS = /[\n\r\t]/g;
const HTML = /[<>]/;
const LETRA = /\p{L}/u;
const NUMERO = /[0-9]/;
const NOMBRE_PERSONA = /^[\p{L}\p{M}'’ -]+$/u;

// ------------------------------------------------------------------ Textos (reglas generales)

/** Recorta los extremos y colapsa los espacios repetidos, como hace el backend antes de guardar */
export const normalizarTexto = (valor) => (valor || '').trim().replace(/\s+/g, ' ');

/** Caracteres de control o signos < > (evita inyección de HTML) */
function errorTextoSeguro(valor, etiqueta, permiteSaltos = false) {
  const revisar = permiteSaltos ? valor.replace(SALTOS, '') : valor;
  if (CONTROL.test(revisar)) return `${etiqueta} contiene caracteres no permitidos`;
  if (HTML.test(valor)) return `${etiqueta} no puede contener los signos < ni >`;
  return '';
}

/**
 * Texto de una línea: obligatorio (si hay mensaje), sin control ni < >, y con largo entre
 * min y max una vez recortado y con los espacios colapsados.
 */
export function validarLinea(valor, { etiqueta, min = 0, max, obligatorio }) {
  const v = valor || '';
  if (!v.trim()) return obligatorio || '';
  const inseguro = errorTextoSeguro(v, etiqueta);
  if (inseguro) return inseguro;
  const t = normalizarTexto(v);
  if (t.length < min) return `${etiqueta} debe tener al menos ${min} caracteres`;
  if (t.length > max) return `${etiqueta} debe tener como máximo ${max} caracteres`;
  return '';
}

// ------------------------------------------------------------------ Registro y login

export function validarNombre(valor) {
  const base = validarLinea(valor, {
    etiqueta: 'El nombre',
    min: NOMBRE_MIN,
    max: NOMBRE_MAX,
    obligatorio: 'El nombre es obligatorio',
  });
  if (base) return base;
  const t = normalizarTexto(valor);
  if (!NOMBRE_PERSONA.test(t)) return 'El nombre solo puede contener letras, espacios, apóstrofos y guiones';
  if (!LETRA.test(t)) return 'El nombre debe contener al menos una letra';
  return '';
}

export function validarCorreo(valor) {
  const t = (valor || '').trim();
  if (!t) return 'El correo es obligatorio';
  if (/\s/.test(t)) return 'El correo no puede contener espacios';
  if (t.length > CORREO_MAX) return `El correo debe tener como máximo ${CORREO_MAX} caracteres`;
  if (!CORREO_VALIDO.test(t)) return 'El correo no tiene un formato válido';
  return '';
}

/**
 * Celular peruano (HU-02): deja solo los 9 dígitos. Acepta espacios, guiones
 * y el prefijo +51 o 51, igual que el backend. '+51 987-654-321' -> '987654321'
 */
export function normalizarCelular(texto) {
  let n = (texto || '').replace(/[\s-]/g, '');
  if (n.startsWith('+51')) n = n.slice(3);
  else if (n.startsWith('51') && n.length === 11) n = n.slice(2);
  return n;
}

export function validarCelular(valor) {
  if (!(valor || '').trim()) return 'El celular es obligatorio';
  if (!CELULAR_VALIDO.test(normalizarCelular(valor))) return 'Ingresa un celular válido de 9 dígitos que empiece con 9';
  return '';
}

/** Requisitos de la contraseña nueva, para mostrar qué falta */
export function requisitosContrasena(valor) {
  const v = valor || '';
  const faltan = CONTRASENA_MIN - v.length;
  return [
    {
      clave: 'min',
      cumple: faltan <= 0,
      texto: faltan > 0 && v ? `Al menos ${CONTRASENA_MIN} caracteres (faltan ${faltan})` : `Al menos ${CONTRASENA_MIN} caracteres`,
    },
    { clave: 'max', cumple: v.length <= CONTRASENA_MAX, texto: `Como máximo ${CONTRASENA_MAX} caracteres` },
    { clave: 'letra', cumple: LETRA.test(v), texto: 'Al menos una letra' },
    { clave: 'numero', cumple: NUMERO.test(v), texto: 'Al menos un número' },
    { clave: 'bordes', cumple: v === v.trim(), texto: 'Sin espacios al inicio ni al final' },
  ];
}

export function validarContrasenaNueva(valor) {
  const v = valor || '';
  if (!v) return 'La contraseña es obligatoria';
  if (v !== v.trim()) return 'La contraseña no puede empezar ni terminar con espacios';
  if (v.length < CONTRASENA_MIN) return `La contraseña debe tener al menos ${CONTRASENA_MIN} caracteres`;
  if (v.length > CONTRASENA_MAX) return `La contraseña debe tener como máximo ${CONTRASENA_MAX} caracteres`;
  if (!LETRA.test(v)) return 'La contraseña debe tener al menos una letra';
  if (!NUMERO.test(v)) return 'La contraseña debe tener al menos un número';
  return '';
}

/** Solo en el frontend: el backend recibe una sola contraseña */
export function validarConfirmacion(valor, contrasena) {
  if (!valor) return 'Repite la contraseña';
  if (valor !== contrasena) return 'Las contraseñas no coinciden';
  return '';
}

/** Login: solo se exige que no esté vacía; si es correcta lo dice el servidor */
export function validarContrasenaIngreso(valor) {
  return (valor || '').trim() ? '' : 'Ingresa tu contraseña';
}

// ------------------------------------------------------------------ Categorías

const NOMBRE_CATEGORIA = /^[\p{L}\p{M}0-9 -]+$/u;
export const MENSAJE_CATEGORIA_REPETIDA = 'Ya existe una categoría con ese nombre (sin distinguir mayúsculas ni tildes)';

/** Clave para comparar sin mayúsculas, tildes ni espacios sobrantes ('Grúas ' -> 'gruas'), como Validador.clave */
export const claveTexto = (valor) => normalizarTexto((valor || '').normalize('NFD').replace(/\p{M}+/gu, '')).toLowerCase();

/** @param existentes categorías ya cargadas, para avisar en vivo si el nombre se repite */
export function validarNombreCategoria(valor, existentes = [], excluirId = null) {
  const base = validarLinea(valor, { etiqueta: 'El nombre', min: 2, max: 80, obligatorio: 'El nombre es obligatorio' });
  if (base) return base;
  const t = normalizarTexto(valor);
  if (!NOMBRE_CATEGORIA.test(t)) return 'El nombre solo puede contener letras, números, espacios y guiones';
  if (!LETRA.test(t)) return 'El nombre debe contener al menos una letra';
  const clave = claveTexto(t);
  if (existentes.some((c) => c.id !== excluirId && claveTexto(c.nombre) === clave)) return MENSAJE_CATEGORIA_REPETIDA;
  return '';
}

export const validarDescripcionCategoria = (valor) => validarLinea(valor, { etiqueta: 'La descripción', max: 255 });

// ------------------------------------------------------------------ Utilidades

/** Aplica { campo: regla } a los datos y devuelve solo los campos con error */
export function validarCampos(reglas, datos) {
  const errores = {};
  Object.entries(reglas).forEach(([campo, regla]) => {
    const mensaje = regla(datos[campo] ?? '', datos);
    if (mensaje) errores[campo] = mensaje;
  });
  return errores;
}

/**
 * Lee los valores actuales de los campos del formulario. Sirve para recoger el
 * autocompletado del navegador, que a veces no dispara onChange hasta que el
 * usuario interactúa con la página.
 */
export function leerCampos(formulario, nombres) {
  const valores = {};
  nombres.forEach((nombre) => {
    const campo = formulario?.elements.namedItem(nombre);
    if (campo && 'value' in campo) valores[nombre] = campo.value;
  });
  return valores;
}
