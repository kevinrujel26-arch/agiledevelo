// Ilustración propia de una excavadora (SVG) e íconos de línea simples.

export function Excavadora({ className = '' }) {
  // Colores desde los tokens del tema (clases exc-* en app.css)
  return (
    <svg className={`excavadora ${className}`} viewBox="0 0 480 360" role="img" aria-label="Ilustración de una excavadora">
      {/* Suelo */}
      <ellipse className="exc-sombra" cx="220" cy="322" rx="190" ry="14" />
      <line className="exc-suelo" x1="30" y1="320" x2="450" y2="320" strokeWidth="2" strokeDasharray="2 10" strokeLinecap="round" />

      <g className="exc-cuerpo" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round">
        {/* Orugas */}
        <rect x="70" y="262" width="232" height="54" rx="27" />
        {/* Cuerpo */}
        <rect x="74" y="208" width="210" height="48" rx="10" />
        <rect x="58" y="218" width="26" height="34" rx="6" />
        {/* Cabina */}
        <path d="M100 138 L178 138 Q186 138 188 146 L202 208 L100 208 Z" />
        {/* Brazo principal (pluma) */}
        <path d="M236 226 L316 90 Q322 80 334 86 L344 92 Q352 98 346 108 L268 232 Z" />
        {/* Balancín */}
        <path d="M322 90 L410 172 Q416 178 410 186 L400 196 Q394 202 386 196 L306 110 Z" />
        {/* Cucharón */}
        <path d="M392 186 Q436 186 440 218 L428 250 Q396 256 372 234 Z" />
        {/* Tubo de escape */}
        <rect x="226" y="186" width="9" height="22" rx="3" strokeWidth="2.5" />
      </g>

      {/* Ventana de la cabina */}
      <path className="exc-cristal" d="M113 152 L172 152 L184 196 L113 196 Z" strokeWidth="2" strokeLinejoin="round" />
      <line className="exc-detalle" x1="140" y1="152" x2="140" y2="196" strokeWidth="2" />

      {/* Ruedas de la oruga */}
      <g className="exc-rueda" strokeWidth="3">
        {[98, 144, 190, 236, 276].map((cx) => (
          <circle key={cx} cx={cx} cy="289" r="14" />
        ))}
      </g>
      <g className="exc-eje">
        {[98, 144, 190, 236, 276].map((cx) => (
          <circle key={cx} cx={cx} cy="289" r="3.5" />
        ))}
      </g>

      {/* Cilindros hidráulicos */}
      <g className="exc-cilindro" strokeWidth="4" strokeLinecap="round">
        <line x1="214" y1="210" x2="296" y2="138" />
        <line x1="338" y1="82" x2="386" y2="150" />
      </g>
      {/* Dientes del cucharón */}
      <g className="exc-dientes" strokeWidth="3" strokeLinecap="round">
        <line x1="428" y1="250" x2="438" y2="262" />
        <line x1="412" y1="253" x2="418" y2="266" />
        <line x1="396" y1="252" x2="398" y2="266" />
      </g>
      {/* Articulaciones */}
      <g className="exc-eje">
        <circle cx="252" cy="222" r="6" />
        <circle cx="330" cy="92" r="6" />
        <circle cx="402" cy="186" r="6" />
      </g>
    </svg>
  );
}

const TRAZOS = {
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  ubicacion: (
    <>
      <path d="M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </>
  ),
  calendario: (
    <>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
      <path d="M3.5 10h17M8 3v4M16 3v4" />
    </>
  ),
  escudo: <path d="M12 3l7.5 3v6c0 4.8-3.3 7.8-7.5 9-4.2-1.2-7.5-4.2-7.5-9V6z" />,
  flecha: <path d="M5 12h14M13 6l6 6-6 6" />,
  rayo: <path d="M13 2.5L4.5 13.5H11L10 21.5l8.5-11H12z" />,
  camion: (
    <>
      <path d="M2.5 6.5h11v9h-11zM13.5 9.5h4l3 3.2v2.8h-7" />
      <circle cx="7" cy="17.5" r="1.8" />
      <circle cx="17" cy="17.5" r="1.8" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8h.01M11 11.5h1V16h1" />
    </>
  ),
  sol: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4" />
    </>
  ),
  luna: <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  cerrar: <path d="M6 6l12 12M18 6L6 18" />,
  buscar: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="M16 16l4.5 4.5" />
    </>
  ),
  correo: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="M3.5 6.5l8.5 6.5 8.5-6.5" />
    </>
  ),
  telefono: <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" />,
  herramienta: <path d="M14.5 6.5a4 4 0 0 0 5 5L12 19a2.1 2.1 0 0 1-3-3l7.5-7.5a4 4 0 0 0-2-2z" />,
};

export function Icono({ nombre, tamanio = 18 }) {
  return (
    <svg
      width={tamanio}
      height={tamanio}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {TRAZOS[nombre]}
    </svg>
  );
}

export function LogoMarca() {
  return (
    <span className="marca-icono" aria-hidden="true">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 17h11l-1.5-5H8l-1-3.5H4.5zM14 12l4-6 2 1-2.5 7" />
        <circle cx="6.5" cy="19" r="1.4" fill="currentColor" />
        <circle cx="11.5" cy="19" r="1.4" fill="currentColor" />
      </svg>
    </span>
  );
}
