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

  // El login normal y el de Google responden igual: { token, expiraEn, recordarme, usuario }
  const guardarSesion = useCallback((r, recordarme) => {
    almacenToken.guardar(r.token, recordarme);
    setAvisoSesion('');
    setUsuario(r.usuario);
  }, []);

  const iniciarSesion = useCallback(
    async ({ correo, contrasena, recordarme }) => {
      const r = await api.post('/auth/login', { correo, contrasena, recordarme });
      guardarSesion(r, recordarme);
      return r.usuario;
    },
    [guardarSesion]
  );

  // "Continuar con Google": el backend verifica el ID token y crea o vincula la cuenta.
  // Devuelve también "nuevo" (cuenta recién creada) para pedirle el celular.
  const iniciarSesionGoogle = useCallback(
    async ({ credential, recordarme = false }) => {
      const r = await api.post('/auth/google', { credential, recordarme });
      guardarSesion(r, recordarme);
      return { usuario: r.usuario, nuevo: Boolean(r.nuevo) };
    },
    [guardarSesion]
  );

  // Completar el celular desde "Mi cuenta"
  const guardarCelular = useCallback(async (telefono) => {
    const r = await api.patch('/auth/yo', { telefono });
    setUsuario(r.usuario);
    return r.usuario;
  }, []);

  // Cambiar contraseña desde "Mi cuenta"
  const cambiarContrasena = useCallback(async (actual, nueva, confirmacion) => {
    const r = await api.patch('/auth/cambiar-contrasena', { actual, nueva, confirmacion });
    setUsuario(r.usuario);
    return r;
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
      iniciarSesionGoogle,
      guardarCelular,
      cambiarContrasena,
      cerrarSesion,
    }),
    [usuario, cargando, avisoSesion, iniciarSesion, iniciarSesionGoogle, guardarCelular, cambiarContrasena, cerrarSesion]
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
