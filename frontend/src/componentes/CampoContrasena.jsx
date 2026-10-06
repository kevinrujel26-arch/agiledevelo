import { useState, useRef } from 'react';

export function CampoContrasena({
  id,
  name,
  value,
  onChange,
  onBlur,
  placeholder,
  autoComplete,
  ...props
}) {
  const [mostrar, setMostrar] = useState(false);
  const inputRef = useRef(null);

  const alToggleOjo = (e) => {
    e.preventDefault();
    setMostrar((m) => !m);
    inputRef.current?.focus();
  };

  return (
    <div className="campo-contrasena-wrapper">
      <input
        ref={inputRef}
        id={id}
        name={name}
        type={mostrar ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        placeholder={placeholder}
        autoComplete={autoComplete}
        {...props}
      />
      <button
        type="button"
        className="boton-ojo"
        onClick={alToggleOjo}
        aria-label={mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        aria-pressed={mostrar}
      >
        {mostrar ? (
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
            <path
              d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
            <path
              d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            />
            <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
            <line x1="2" y1="2" x2="22" y2="22" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        )}
      </button>
    </div>
  );
}
