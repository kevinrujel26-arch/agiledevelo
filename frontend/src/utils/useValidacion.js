// Estado de la validación en vivo de un formulario: qué campos ya se tocaron y qué errores
// devolvió el servidor. Las reglas vienen de utils/validaciones.js.
import { useState } from 'react';
import { validarCampos } from './validaciones';

export function useValidacion(reglas, datos, tocadosIniciales = {}) {
  // Un campo muestra su error desde que se escribe en él o se sale de él
  const [tocados, setTocados] = useState(tocadosIniciales);
  const [erroresServidor, setErroresServidor] = useState({});

  const errores = validarCampos(reglas, datos);
  const hayErrores = Object.keys(errores).length > 0;

  return {
    hayErrores,
    tocados,
    /** Error visible: el del servidor (hasta que se vuelve a editar el campo) o el de la regla si ya se tocó */
    errorDe: (campo) => erroresServidor[campo] || (tocados[campo] ? errores[campo] || '' : ''),
    esValido: (campo) => Boolean(tocados[campo]) && !errores[campo] && !erroresServidor[campo],
    tocar: (...campos) => setTocados((t) => ({ ...t, ...Object.fromEntries(campos.map((c) => [c, true])) })),
    /** Marca el campo como tocado y olvida el error del servidor para ese campo */
    editado: (campo) => {
      setTocados((t) => (t[campo] ? t : { ...t, [campo]: true }));
      setErroresServidor((e) => (e[campo] ? { ...e, [campo]: undefined } : e));
    },
    tocarTodos: () => setTocados((t) => ({ ...t, ...Object.fromEntries(Object.keys(reglas).map((c) => [c, true])) })),
    reiniciar: (nuevosTocados = {}) => {
      setTocados(nuevosTocados);
      setErroresServidor({});
    },
    /** Errores por campo que devolvió el backend ({ campo: mensaje }) */
    setErroresServidor,
  };
}
