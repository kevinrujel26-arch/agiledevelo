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

// ------------------------------------------------------------------ Números

/**
 * Deja solo dígitos y un punto decimal (la coma se toma como punto). Se usa en los campos
 * de montos para bloquear letras y signos mientras se escribe.
 */
export function limpiarDecimal(valor) {
  const t = (valor || '').replace(/,/g, '.').replace(/[^0-9.]/g, '');
  const punto = t.indexOf('.');
  return punto === -1 ? t : t.slice(0, punto + 1) + t.slice(punto + 1).replace(/\./g, '');
}

/** Número decimal, igual que Validador.decimal: formato, signo, decimales y máximo, en ese orden */
export function validarDecimal(valor, { etiqueta, obligatorio, mayorQueCero, maximo, maxDecimales }) {
  const t = String(valor ?? '').trim();
  if (!t) return obligatorio || '';
  if (!/^-?\d+(\.\d+)?$/.test(t)) return `${etiqueta} debe ser un número`;
  const n = Number(t);
  if (mayorQueCero && n <= 0) return `${etiqueta} debe ser mayor a 0`;
  if (n < 0) return `${etiqueta} no puede ser negativo`;
  const decimales = (t.split('.')[1] || '').replace(/0+$/, '').length;
  if (decimales > maxDecimales) {
    return `${etiqueta} puede tener como máximo ${maxDecimales} ${maxDecimales === 1 ? 'decimal' : 'decimales'}`;
  }
  if (maximo != null && n > maximo) return `${etiqueta} no puede ser mayor a ${maximo}`;
  return '';
}

// ------------------------------------------------------------------ Máquinas

const TEXTO_MAQUINA = /^[\p{L}\p{M}0-9 ./+()-]+$/u;
const LETRA_O_NUMERO = /[\p{L}0-9]/u;
export const TARIFA_MAXIMA = 100000;
export const HOROMETRO_MAXIMO = 999999.9;

/** Nombre, marca o modelo: letras, números, espacios y - . / + ( ), con alguna letra o número */
function validarTextoMaquina(valor, etiqueta, min, max, obligatorio) {
  const base = validarLinea(valor, { etiqueta, min, max, obligatorio });
  if (base) return base;
  const t = normalizarTexto(valor);
  if (!TEXTO_MAQUINA.test(t)) return `${etiqueta} solo puede contener letras, números, espacios y los signos - . / + ( )`;
  if (!LETRA_O_NUMERO.test(t)) return `${etiqueta} debe contener al menos una letra o un número`;
  return '';
}

export const validarNombreMaquina = (v) => validarTextoMaquina(v, 'El nombre', 2, 120, 'El nombre es obligatorio');
export const validarMarca = (v) => validarTextoMaquina(v, 'La marca', 1, 80, 'La marca es obligatoria');
export const validarModelo = (v) => validarTextoMaquina(v, 'El modelo', 1, 80, 'El modelo es obligatorio');

export function validarUbicacion(valor) {
  const base = validarLinea(valor, { etiqueta: 'La ubicación', min: 2, max: 160, obligatorio: 'La ubicación es obligatoria' });
  if (base) return base;
  return LETRA.test(valor) ? '' : 'La ubicación debe contener al menos una letra';
}

/** Texto de varias líneas y opcional (descripciones): conserva los saltos de línea */
export function validarParrafo(valor, etiqueta, max) {
  const v = valor || '';
  if (!v.trim()) return '';
  const inseguro = errorTextoSeguro(v, etiqueta, true);
  if (inseguro) return inseguro;
  return v.trim().length > max ? `${etiqueta} debe tener como máximo ${max} caracteres` : '';
}

export const validarDescripcionMaquina = (v) => validarParrafo(v, 'La descripción', 2000);

export const validarTarifa = (v) =>
  validarDecimal(v, {
    etiqueta: 'La tarifa por hora',
    obligatorio: 'La tarifa por hora es obligatoria',
    mayorQueCero: true,
    maximo: TARIFA_MAXIMA,
    maxDecimales: 2,
  });

export const validarHorometro = (v) =>
  validarDecimal(v, { etiqueta: 'El horómetro inicial', maximo: HOROMETRO_MAXIMO, maxDecimales: 1 });

/** Filas de especificaciones que tienen algo escrito (las vacías se ignoran) */
const filasUsadas = (filas) => filas.filter((f) => f.clave.trim() || f.valor.trim());

/**
 * Especificaciones técnicas, como Validador.especificaciones: máximo 30, nombre ≤ 60, valor ≤ 200,
 * sin nombres vacíos ni repetidos (sin distinguir mayúsculas ni tildes).
 * Devuelve el error de cada fila (en el mismo orden) y uno general.
 */
export function erroresEspecificaciones(filas) {
  const vistas = new Set();
  const porFila = filas.map((f) => {
    if (!f.clave.trim() && !f.valor.trim()) return '';
    const nombre = normalizarTexto(f.clave);
    const valor = normalizarTexto(f.valor);
    if (!nombre) return 'Cada especificación necesita un nombre';
    if (CONTROL.test(f.clave + f.valor)) return 'Las especificaciones contienen caracteres no permitidos';
    if (HTML.test(f.clave + f.valor)) return 'Las especificaciones no pueden contener los signos < ni >';
    if (nombre.length > 60) return 'El nombre de una especificación debe tener como máximo 60 caracteres';
    if (valor.length > 200) return `El valor de "${nombre}" debe tener como máximo 200 caracteres`;
    const clave = claveTexto(nombre);
    if (vistas.has(clave)) return `La especificación "${nombre}" está repetida`;
    vistas.add(clave);
    return '';
  });
  const general = filasUsadas(filas).length > 30 ? 'Puedes agregar como máximo 30 especificaciones' : '';
  return { porFila, general, hayErrores: Boolean(general) || porFila.some(Boolean) };
}

/** Filas -> objeto { nombre: valor } con los textos ya normalizados, como lo guarda el backend */
export const especificacionesComoObjeto = (filas) =>
  Object.fromEntries(filasUsadas(filas).map((f) => [normalizarTexto(f.clave), normalizarTexto(f.valor)]));

// ------------------------------------------------------------------ Fotos

export const MAX_FOTOS = 5;
export const MAX_MB_FOTO = 5;
const TIPOS_FOTO = ['image/jpeg', 'image/png'];

/** Revisa las fotos elegidas antes de subirlas, con los mismos mensajes del backend */
export function validarFotos(archivos, yaSubidas) {
  const disponibles = MAX_FOTOS - yaSubidas;
  if (archivos.length > disponibles) {
    return disponibles > 0
      ? `La máquina ya tiene ${yaSubidas} foto(s). Solo puedes subir ${disponibles} más (máximo ${MAX_FOTOS})`
      : `La máquina ya tiene el máximo de ${MAX_FOTOS} fotos`;
  }
  for (const a of archivos) {
    if (!TIPOS_FOTO.includes(a.type)) return `Solo se permiten fotos en formato JPG o PNG ("${a.name}")`;
    if (a.size > MAX_MB_FOTO * 1024 * 1024) return `Cada foto puede pesar como máximo ${MAX_MB_FOTO} MB ("${a.name}")`;
  }
  return '';
}

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
