// Tema claro/oscuro. El script de index.html aplica el tema inicial antes de cargar React;
// aquí se lee, se cambia y se recuerda la elección (o se sigue al sistema si no hay elección).
import { useEffect, useState } from 'react';

const CLAVE_TEMA = 'maquirenta.tema';
const COLOR_BARRA = { light: '#faf7f2', dark: '#0a0c10' };
const consultaOscuro = () => window.matchMedia?.('(prefers-color-scheme: dark)');

function temaGuardado() {
  try {
    const t = localStorage.getItem(CLAVE_TEMA);
    return t === 'light' || t === 'dark' ? t : null;
  } catch {
    return null;
  }
}

function aplicarTema(tema, conTransicion) {
  const raiz = document.documentElement;
  if (conTransicion) {
    // Transición suave solo durante el cambio (no al cargar la página)
    raiz.classList.add('transicion-tema');
    window.setTimeout(() => raiz.classList.remove('transicion-tema'), 350);
  }
  raiz.setAttribute('data-theme', tema);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', COLOR_BARRA[tema]);
}

export function useTema() {
  const [tema, setTema] = useState(() => document.documentElement.getAttribute('data-theme') || 'dark');

  // Sin elección guardada, el tema sigue los cambios del sistema
  useEffect(() => {
    const consulta = consultaOscuro();
    if (!consulta) return undefined;
    const alCambiar = (e) => {
      if (temaGuardado()) return;
      const nuevo = e.matches ? 'dark' : 'light';
      aplicarTema(nuevo, true);
      setTema(nuevo);
    };
    consulta.addEventListener('change', alCambiar);
    return () => consulta.removeEventListener('change', alCambiar);
  }, []);

  const alternar = () => {
    const nuevo = tema === 'dark' ? 'light' : 'dark';
    try {
      localStorage.setItem(CLAVE_TEMA, nuevo);
    } catch {
      // sin almacenamiento (modo privado): el cambio vale solo para esta visita
    }
    aplicarTema(nuevo, true);
    setTema(nuevo);
  };

  return { tema, alternar };
}
