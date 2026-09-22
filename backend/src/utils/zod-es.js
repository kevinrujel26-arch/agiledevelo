// Mensajes de validación de Zod en español (se aplican a todos los esquemas)
const { z } = require('zod');

z.setErrorMap((issue, ctx) => {
  switch (issue.code) {
    case z.ZodIssueCode.invalid_type:
      if (issue.received === 'undefined' || issue.received === 'null') {
        return { message: 'Este campo es obligatorio' };
      }
      return { message: 'Tipo de dato inválido' };
    case z.ZodIssueCode.too_small:
      if (issue.type === 'string') return { message: `Debe tener al menos ${issue.minimum} caracteres` };
      if (issue.type === 'array') return { message: `Debe tener al menos ${issue.minimum} elemento(s)` };
      return { message: `Debe ser mayor${issue.inclusive ? ' o igual' : ''} a ${issue.minimum}` };
    case z.ZodIssueCode.too_big:
      if (issue.type === 'string') return { message: `Debe tener como máximo ${issue.maximum} caracteres` };
      if (issue.type === 'array') return { message: `Debe tener como máximo ${issue.maximum} elemento(s)` };
      return { message: `Debe ser menor${issue.inclusive ? ' o igual' : ''} a ${issue.maximum}` };
    case z.ZodIssueCode.invalid_string:
      if (issue.validation === 'email') return { message: 'El correo no tiene un formato válido' };
      return { message: 'Formato inválido' };
    case z.ZodIssueCode.invalid_enum_value:
      return { message: `Valor inválido. Opciones: ${issue.options.join(', ')}` };
    case z.ZodIssueCode.unrecognized_keys:
      return { message: `Campos no permitidos: ${issue.keys.join(', ')}` };
    default:
      return { message: ctx.defaultError };
  }
});

module.exports = z;
