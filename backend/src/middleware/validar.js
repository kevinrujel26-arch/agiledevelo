const { errores } = require('../utils/errores');

function detallesZod(error) {
  return error.issues.map((i) => ({ campo: i.path.join('.'), mensaje: i.message }));
}

/**
 * Valida params, query y body con esquemas de Zod.
 * Los datos ya limpios quedan en req.datos.params / req.datos.query / req.datos.body
 *   router.post('/', validar({ body: esquema }), controlador)
 */
function validar(esquemas) {
  return (req, _res, next) => {
    req.datos = req.datos || {};
    for (const parte of ['params', 'query', 'body']) {
      if (!esquemas[parte]) continue;
      const resultado = esquemas[parte].safeParse(req[parte] ?? {});
      if (!resultado.success) {
        const detalles = detallesZod(resultado.error);
        const primero = detalles[0];
        const mensaje = primero.campo ? `${primero.campo}: ${primero.mensaje}` : primero.mensaje;
        return next(errores.solicitudInvalida(mensaje, detalles));
      }
      req.datos[parte] = resultado.data;
    }
    next();
  };
}

module.exports = { validar, detallesZod };
