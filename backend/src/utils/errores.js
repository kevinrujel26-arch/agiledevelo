/** Error de negocio con código HTTP. El manejador global lo convierte en JSON. */
class ErrorApp extends Error {
  constructor(estadoHttp, mensaje, detalles) {
    super(mensaje);
    this.name = 'ErrorApp';
    this.estadoHttp = estadoHttp;
    this.detalles = detalles;
  }
}

const errores = {
  solicitudInvalida: (mensaje, detalles) => new ErrorApp(400, mensaje, detalles),
  noAutenticado: (mensaje = 'Debes iniciar sesión') => new ErrorApp(401, mensaje),
  prohibido: (mensaje = 'No tienes permiso para realizar esta acción') => new ErrorApp(403, mensaje),
  noEncontrado: (mensaje = 'Recurso no encontrado') => new ErrorApp(404, mensaje),
  conflicto: (mensaje, detalles) => new ErrorApp(409, mensaje, detalles),
  bloqueado: (mensaje) => new ErrorApp(423, mensaje),
};

module.exports = { ErrorApp, errores };
