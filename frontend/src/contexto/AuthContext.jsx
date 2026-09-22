import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, almacenToken } from '../api/cliente';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [avisoSesion, setAvisoSesion] = useState('');

  // Al abrir la app, si hay token, validamos que la sesión siga vigente
  useEffect(() => {
    if (!almacenToken.obtener()) {
      setCargando(false);
      return;
    }
    api
      .get('/auth/yo')
      .then((r) => setUsuario(r.usuario))
      .catch(() => almacenToken.borrar())
      .finally(() => setCargando(false));
  }, []);

  // HU-02 criterio 3: si el backend dice que la sesión expiró, la cerramos aquí también
  useEffect(() => {
    const alExpirar = (e) => {
      almacenToken.borrar();
      setUsuario(null);
      setAvisoSesion(e.detail || 'Tu sesión expiró. Inicia sesión nuevamente');
    };
    window.addEventListener('sesion-expirada', alExpirar);
    return () => window.removeEventListener('sesion-expirada', alExpirar);
  }, []);

  const iniciarSesion = useCallback(async ({ correo, contrasena, recordarme }) => {
    const r = await api.post('/auth/login', { correo, contrasena, recordarme });
    almacenToken.guardar(r.token, recordarme);
    setAvisoSesion('');
    setUsuario(r.usuario);
    return r.usuario;
  }, []);

  const cerrarSesion = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // aunque falle la red, cerramos la sesión localmente
    }
    almacenToken.borrar();
    setUsuario(null);
  }, []);

  const valor = useMemo(
    () => ({
      usuario,
      cargando,
      esAdmin: usuario?.rol === 'ADMINISTRADOR',
      avisoSesion,
      limpiarAviso: () => setAvisoSesion(''),
      iniciarSesion,
      cerrarSesion,
    }),
    [usuario, cargando, avisoSesion, iniciarSesion, cerrarSesion]
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const contexto = useContext(AuthContext);
  if (!contexto) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return contexto;
}

/** Ruta del panel según el rol (HU-02 criterio 1) */
export function panelSegunRol(usuario) {
  return usuario?.rol === 'ADMINISTRADOR' ? '/admin/maquinas' : '/mi-cuenta';
}
