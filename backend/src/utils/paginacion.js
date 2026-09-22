const { z } = require('zod');

const esquemaPaginacion = {
  pagina: z.coerce.number().int().min(1).default(1),
  tamanio: z.coerce.number().int().min(1).max(50).default(12),
};

function limiteDesplazamiento({ pagina, tamanio }) {
  return { limite: tamanio, desplazamiento: (pagina - 1) * tamanio };
}

function respuestaPaginada(datos, total, { pagina, tamanio }) {
  return {
    datos,
    paginacion: {
      pagina,
      tamanio,
      total,
      totalPaginas: Math.max(1, Math.ceil(total / tamanio)),
    },
  };
}

module.exports = { esquemaPaginacion, limiteDesplazamiento, respuestaPaginada };
