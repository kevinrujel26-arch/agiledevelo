// Reglas de validación de formularios (las mismas que aplica el backend en Validador.java).
// Cada regla recibe el valor y devuelve el mensaje de error, o '' si es válido.

export const NOMBRE_MAX = 120;
export const CORREO_MAX = 160;
export const CONTRASENA_MIN = 8;
export const CONTRASENA_MAX = 72;

export const CORREO_VALIDO = /^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
export const CELULAR_VALIDO = /^9\d{8}$/;

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

export function validarNombre(valor) {
  const t = valor.trim();
  if (!t) return 'El nombre es obligatorio';
  if (t.length > NOMBRE_MAX) return `El nombre debe tener como máximo ${NOMBRE_MAX} caracteres`;
  return '';
}

export function validarCorreo(valor) {
  const t = valor.trim();
  if (!t) return 'El correo es obligatorio';
  if (t.length > CORREO_MAX) return `El correo debe tener como máximo ${CORREO_MAX} caracteres`;
  if (!CORREO_VALIDO.test(t)) return 'El correo no tiene un formato válido';
  return '';
}

export function validarCelular(valor) {
  if (!valor.trim()) return 'El celular es obligatorio';
  if (!CELULAR_VALIDO.test(normalizarCelular(valor))) return 'Ingresa un celular válido de 9 dígitos que empiece con 9';
  return '';
}

/** Requisitos de la contraseña nueva, para mostrar qué falta */
export function requisitosContrasena(valor) {
  const faltan = CONTRASENA_MIN - valor.length;
  return [
    {
      clave: 'min',
      cumple: faltan <= 0,
      texto: faltan > 0 && valor ? `Al menos ${CONTRASENA_MIN} caracteres (faltan ${faltan})` : `Al menos ${CONTRASENA_MIN} caracteres`,
    },
    { clave: 'max', cumple: valor.length <= CONTRASENA_MAX, texto: `Como máximo ${CONTRASENA_MAX} caracteres` },
  ];
}

export function validarContrasenaNueva(valor) {
  if (!valor) return 'La contraseña es obligatoria';
  if (valor.length < CONTRASENA_MIN) return `La contraseña debe tener al menos ${CONTRASENA_MIN} caracteres`;
  if (valor.length > CONTRASENA_MAX) return `La contraseña debe tener como máximo ${CONTRASENA_MAX} caracteres`;
  return '';
}

export function validarConfirmacion(valor, contrasena) {
  if (!valor) return 'Repite la contraseña';
  if (valor !== contrasena) return 'Las contraseñas no coinciden';
  return '';
}

/** Login: solo se exige que no esté vacía; si es correcta lo dice el servidor */
export function validarContrasenaIngreso(valor) {
  return valor.trim() ? '' : 'Ingresa tu contraseña';
}

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
