import { useEffect, useState } from 'react';
import { api, ErrorApi } from '../api/cliente';
import { useAuth } from '../contexto/AuthContext';
import { hoyISO, formatearMoneda } from '../utils/formato';
import { validarRuc, validarDni } from '../utils/validadorRuc';
import { validarLinea, normalizarCelular, validarCelular, validarCorreo, normalizarTexto } from '../utils/validaciones';
import { Icono } from './Ilustracion';
import { Alerta } from './comunes';
import PagoTarjeta from './PagoTarjeta';
import { createPortal } from 'react-dom';
import { TEXTO_TERMINOS } from '../utils/textoTerminos';

const pad = (n) => String(n).padStart(2, '0');
const DIAS_SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

function nombreMes(anio, mes) {
  const mesTexto = new Intl.DateTimeFormat('es-PE', { month: 'long' }).format(new Date(anio, mes, 1));
  return `${mesTexto.charAt(0).toUpperCase()}${mesTexto.slice(1)} ${anio}`;
}

export default function FormularioReserva({ maquina, ocupados, onExito, onCerrar }) {
  const { usuario } = useAuth();
  const hoy = hoyISO();

  // Estado: navegación del flujo
  const [paso, setPaso] = useState(1);

  // Estado: selección de día y hora
  const [diaSeleccionado, setDiaSeleccionado] = useState('');
  const [horaSeleccionada, setHoraSeleccionada] = useState(null);
  const [cantidadHoras, setCantidadHoras] = useState(1);
  const [horas, setHoras] = useState([]);
  const [cargandoHoras, setCargandoHoras] = useState(false);

  // Estado: formulario solicitante
  const [formulario, setFormulario] = useState({
    nombreSolicitante: '',
    ruc: '',
    tipoDoc: 'RUC',
    telefono: usuario?.telefono || '',
    email: usuario?.correo || '',
    ubicacionObra: '',
    detalle: '',
    aceptaPolitica: false,
  });
  const [errores, setErrores] = useState({});

  // Estado: envío
  const [enviando, setEnviando] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState('');
  const [codigoReserva, setCodigoReserva] = useState('');
  const [reservaCreada, setReservaCreada] = useState(null);
  // HU-07: formulario de tarjeta dentro de la página (si el servidor lo activa) o redirección a Mercado Pago
  const [pagando, setPagando] = useState(false);
  const [verPago, setVerPago] = useState(false);
  const [pagoHecho, setPagoHecho] = useState(false);

  async function pagarAhora() {
    setPagando(true);
    setErrorEnvio('');
    try {
      let enPagina = false;
      try {
        const c = await api.get('/pagos/config');
        enPagina = c.datos?.pagoEnPagina === true;
      } catch {
        enPagina = false;
      }
      if (enPagina) {
        setVerPago(true);
        setPagando(false);
        return;
      }
      const r = await api.post(`/reservas/${reservaCreada.id}/pagar`, {});
      window.location.assign(r.datos.urlPago);
    } catch (e) {
      setErrorEnvio(e.message);
      setPagando(false);
    }
  }
  const [verTerminos, setVerTerminos] = useState(false);

  // Efecto: auto-seleccionar primer día con hora LIBRE
  useEffect(() => {
    if (diaSeleccionado || horas.length === 0) return;

    const primerDiaConHoraLibre = async () => {
      for (let offset = 0; offset <= 30; offset++) {
        const fecha = new Date(hoy);
        fecha.setDate(fecha.getDate() + offset);
        const fechaStr = fecha.toISOString().slice(0, 10);

        // Verificar que no esté ocupado
        const estaOcupado = ocupados.some((o) => o.fechaInicio <= fechaStr && fechaStr <= o.fechaFin);
        if (estaOcupado) continue;

        try {
          const res = await api.get(`/maquinas/${maquina.id}/disponibilidad/horas`, { fecha: fechaStr });
          const horasDelDia = res.datos.horas || [];
          if (horasDelDia.some((h) => h.estado === 'LIBRE')) {
            setDiaSeleccionado(fechaStr);
            setHoras(horasDelDia);
            return;
          }
        } catch (e) {
          console.error('Error cargando horas:', e);
        }
      }
    };

    primerDiaConHoraLibre();
  }, []);

  // Mini-calendario para seleccionar día
  function SelectorDia() {
    const [mesActual, setMesActual] = useState(() => {
      const [a, m] = hoy.split('-');
      return { anio: parseInt(a), mes: parseInt(m) - 1 };
    });

    const estaOcupado = (iso) => ocupados.some((o) => o.fechaInicio <= iso && iso <= o.fechaFin);
    const esPasado = (iso) => iso < hoy;
    const puedeSeleccionar = (iso) => !estaOcupado(iso) && !esPasado(iso);

    const primerDia = (new Date(mesActual.anio, mesActual.mes, 1).getDay() + 6) % 7;
    const diasEnMes = new Date(mesActual.anio, mesActual.mes + 1, 0).getDate();
    const hoyMesAnio = hoy.slice(0, 7);
    const mesActualStr = `${mesActual.anio}-${pad(mesActual.mes + 1)}`;

    return (
      <div className="selector-dia">
        <div className="sel-controles">
          <button
            type="button"
            onClick={() => {
              const f = new Date(mesActual.anio, mesActual.mes - 1, 1);
              setMesActual({ anio: f.getFullYear(), mes: f.getMonth() });
            }}
            className="boton boton-chico boton-secundario"
            disabled={mesActualStr <= hoyMesAnio}
          >
            ←
          </button>
          <h3 className="sel-titulo">{nombreMes(mesActual.anio, mesActual.mes)}</h3>
          <button
            type="button"
            onClick={() => {
              const f = new Date(mesActual.anio, mesActual.mes + 1, 1);
              setMesActual({ anio: f.getFullYear(), mes: f.getMonth() });
            }}
            className="boton boton-chico boton-secundario"
          >
            →
          </button>
        </div>

        <div className="cal-rejilla">
          {DIAS_SEMANA.map((d, i) => (
            <div key={i} className="cal-semana">{d}</div>
          ))}
          {Array.from({ length: primerDia }).map((_, i) => (
            <div key={`v${i}`} />
          ))}
          {Array.from({ length: diasEnMes }, (_, i) => {
            const dia = i + 1;
            const iso = `${mesActual.anio}-${pad(mesActual.mes + 1)}-${pad(dia)}`;
            const puedeSelec = puedeSeleccionar(iso);
            return (
              <button
                key={iso}
                type="button"
                onClick={() => {
                  setDiaSeleccionado(iso);
                  setHoraSeleccionada(null);
                  setCantidadHoras(1);
                  cargarHoras(iso);
                }}
                disabled={!puedeSelec}
                className={`cal-dia-boton ${diaSeleccionado === iso ? 'activo' : ''}`}
                aria-pressed={diaSeleccionado === iso}
              >
                {dia}
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  // Cargar horas disponibles para un día
  async function cargarHoras(fecha) {
    setCargandoHoras(true);
    try {
      const res = await api.get(`/maquinas/${maquina.id}/disponibilidad/horas`, { fecha });
      setHoras(res.datos.horas || []);
    } catch (e) {
      console.error('Error al cargar horas:', e);
      setHoras([]);
    } finally {
      setCargandoHoras(false);
    }
  }

  // Cuadrícula de horas
  function CuadriculaHoras() {
    if (!diaSeleccionado) return null;
    if (cargandoHoras) return <div className="cargando"><div className="spinner" /></div>;
    if (!horas.length) return <Alerta>No hay horas disponibles para este día.</Alerta>;

    const horaSelec = horaSeleccionada;

    return (
      <div>
        <div className="subtitulo titulo-horas">Horas disponibles</div>
        <div className="cuadricula-horas">
          {horas.map((h) => {
            const esLibre = h.estado === 'LIBRE';
            const esPreparacion = h.estado === 'PREPARACION';
            const esReservada = h.estado === 'RESERVADA';
            const esPasada = h.estado === 'PASADA';

            let clase = 'hora-boton';
            if (horaSelec === h.hora) clase += ' activa';
            if (esPreparacion) clase += ' preparacion';
            if (esReservada) clase += ' reservada';
            if (esPasada) clase += ' pasada';

            const tooltipText = esPasada
              ? 'Ya no hay tiempo para reservar esta hora (mínimo 2 horas de anticipación)'
              : esPreparacion
                ? 'Tiempo de preparación de la máquina'
                : esReservada
                  ? 'Hora reservada'
                  : '';

            return (
              <button
                key={h.hora}
                type="button"
                onClick={() => {
                  if (esLibre) {
                    setHoraSeleccionada(h.hora);
                    setCantidadHoras(1);
                  }
                }}
                disabled={!esLibre}
                className={clase}
                title={tooltipText}
                aria-pressed={horaSelec === h.hora}
                aria-label={`${pad(h.hora)}:00 - ${h.estado === 'LIBRE' ? 'disponible' : 'no disponible'}`}
              >
                {pad(h.hora)}:00
              </button>
            );
          })}
        </div>

        {horaSelec !== null && (
          <div className="selector-cantidad">
            <label htmlFor="cantidadHoras">Cantidad de horas:</label>
            <div className="cantidad-controles">
              <button
                type="button"
                onClick={() => setCantidadHoras((c) => Math.max(1, c - 1))}
                className="boton boton-chico boton-secundario"
                disabled={cantidadHoras <= 1}
              >
                −
              </button>
              <input
                id="cantidadHoras"
                type="number"
                min="1"
                max={horas.find((h) => h.hora === horaSelec)?.horasMaximas || 12}
                value={cantidadHoras}
                onChange={(e) => {
                  const v = parseInt(e.target.value) || 1;
                  const max = horas.find((h) => h.hora === horaSelec)?.horasMaximas || 12;
                  setCantidadHoras(Math.min(max, Math.max(1, v)));
                }}
              />
              <button
                type="button"
                onClick={() => {
                  const max = horas.find((h) => h.hora === horaSelec)?.horasMaximas || 12;
                  setCantidadHoras((c) => Math.min(max, c + 1));
                }}
                className="boton boton-chico boton-secundario"
                disabled={cantidadHoras >= (horas.find((h) => h.hora === horaSelec)?.horasMaximas || 12)}
              >
                +
              </button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // Resumen
  function Resumen() {
    if (!diaSeleccionado || horaSeleccionada === null) return null;

    const horaFin = (horaSeleccionada + cantidadHoras) % 24;
    const combustible = maquina.combustiblePorHora || 0;
    const total = Math.round((maquina.tarifaHoraria + combustible) * cantidadHoras * 100) / 100;

    return (
      <div className="resumen-reserva">
        <div>
          <div>
            <span>Fecha:</span>
            <strong>{diaSeleccionado}</strong>
          </div>
          <div>
            <span>Horario:</span>
            <strong>
              {pad(horaSeleccionada)}:00 - {pad(horaFin)}:00
            </strong>
          </div>
          <div>
            <span>Horas:</span>
            <strong>{cantidadHoras} {cantidadHoras === 1 ? 'hora' : 'horas'}</strong>
          </div>
          <div>
            <span>Tarifa ({formatearMoneda(maquina.tarifaHoraria)} × {cantidadHoras} h):</span>
            <strong>{formatearMoneda(maquina.tarifaHoraria * cantidadHoras)}</strong>
          </div>
          {combustible > 0 && (
            <div>
              <span>Combustible ({formatearMoneda(combustible)} × {cantidadHoras} h):</span>
              <strong>{formatearMoneda(combustible * cantidadHoras)}</strong>
            </div>
          )}
          <div>
            <span>Operador:</span>
            <strong>Incluido</strong>
          </div>
          <div>
            <span className="resumen-total">Total:</span>
            <strong>{formatearMoneda(total)}</strong>
          </div>
        </div>
        <p className="ayuda">
          <Icono nombre="info" /> El costo de movilización, si corresponde, se coordina con el administrador y no está incluido en el total mostrado.
        </p>
        <p className="ayuda">
          <Icono nombre="info" /> Servicio disponible solo dentro de la ciudad de Trujillo.
        </p>
      </div>
    );
  }

  // Validar y cambiar campos del formulario
  function cambiarCampo(nombre, valor) {
    setFormulario((prev) => ({ ...prev, [nombre]: valor }));
    if (errores[nombre]) {
      setErrores((prev) => {
        const nuevos = { ...prev };
        delete nuevos[nombre];
        return nuevos;
      });
    }
  }

  // Validar formulario
  function validarFormulario() {
    const nuevosErrores = {};

    const nombreError = validarLinea(formulario.nombreSolicitante, {
      etiqueta: 'Nombre completo',
      min: 1,
      max: 160,
      obligatorio: 'El nombre completo es obligatorio',
    });
    if (nombreError) nuevosErrores.nombreSolicitante = nombreError;

    const rucError = formulario.tipoDoc === 'DNI' ? validarDni(formulario.ruc) : validarRuc(formulario.ruc);
    if (rucError) nuevosErrores.ruc = rucError;

    const telefonoError = validarCelular(formulario.telefono);
    if (telefonoError) nuevosErrores.telefono = telefonoError;

    const correoError = validarCorreo(formulario.email);
    if (correoError) nuevosErrores.email = correoError;

    const ubicacionError = validarLinea(formulario.ubicacionObra, {
      etiqueta: 'Ubicación de la obra',
      min: 1,
      max: 200,
      obligatorio: 'La ubicación de la obra es obligatoria',
    });
    if (ubicacionError) nuevosErrores.ubicacionObra = ubicacionError;

    const detalleError = validarLinea(formulario.detalle, {
      etiqueta: 'Detalle',
      max: 300,
      obligatorio: '',
    });
    if (detalleError) nuevosErrores.detalle = detalleError;

    if (!formulario.aceptaPolitica) {
      nuevosErrores.aceptaPolitica = 'Debes aceptar los términos y condiciones';
    }

    setErrores(nuevosErrores);
    return Object.keys(nuevosErrores).length === 0;
  }

  // Enviar reserva
  async function enviarReserva() {
    if (!validarFormulario()) return;
    if (!diaSeleccionado || horaSeleccionada === null) {
      setErrorEnvio('Debes seleccionar día y hora');
      return;
    }

    setEnviando(true);
    setErrorEnvio('');

    try {
      const cuerpo = {
        maquinaId: maquina.id,
        fecha: diaSeleccionado,
        horaInicio: horaSeleccionada,
        horas: cantidadHoras,
        nombreSolicitante: normalizarTexto(formulario.nombreSolicitante),
        ruc: formulario.ruc,
        telefono: normalizarCelular(formulario.telefono),
        email: formulario.email.trim(),
        ubicacionObra: normalizarTexto(formulario.ubicacionObra),
        detalle: formulario.detalle ? normalizarTexto(formulario.detalle) : undefined,
        aceptaPolitica: formulario.aceptaPolitica,
      };

      const res = await api.post('/reservas', cuerpo);

      setCodigoReserva(res.codigo);
      setReservaCreada({
        id: res.datos?.id,
        fecha: diaSeleccionado,
        horaInicio: horaSeleccionada,
        horas: cantidadHoras,
        total: res.datos?.total ?? (maquina.tarifaHoraria + (maquina.combustiblePorHora || 0)) * cantidadHoras,
      });
      setPaso(3);
    } catch (e) {
      if (e instanceof ErrorApi) {
        if (e.estado === 403) {
          setErrorEnvio('Necesitas tener un celular registrado para reservar.');
        } else if (e.estado === 409) {
          if (e.message.includes('pendientes')) {
            setErrorEnvio('Tienes reservas pendientes de pago. Págalas o cancélalas antes de reservar otra.');
          } else if (e.message.includes('ya no')) {
            setErrorEnvio('Ese horario ya no está disponible. Por favor, elige otro.');
            if (diaSeleccionado) cargarHoras(diaSeleccionado);
          }
        } else if (e.estado === 400 && e.detalles) {
          const nuevoErrores = {};
          e.detalles.forEach((d) => {
            if (d.campo) nuevoErrores[d.campo] = d.mensaje;
          });
          if (Object.keys(nuevoErrores).length > 0) {
            setErrores(nuevoErrores);
            setErrorEnvio('Revisa los errores en el formulario.');
          } else {
            setErrorEnvio(e.message);
          }
        } else {
          setErrorEnvio(e.message);
        }
      } else {
        setErrorEnvio('Ocurrió un error al enviar la reserva.');
      }
    } finally {
      setEnviando(false);
    }
  }

  // Confirmación de reserva
  function Confirmacion() {
    const horaFin = (reservaCreada.horaInicio + reservaCreada.horas) % 24;

    return (
      <div className="confirmacion-reserva" role="status" aria-live="polite">
        <div className="confirmacion-icono" aria-hidden="true">✓</div>
        <h2 className="confirmacion-titulo">¡Reserva registrada!</h2>
        <p className="texto-suave confirmacion-codigo-label">Código de reserva:</p>
        <div className="confirmacion-codigo">
          {codigoReserva}
        </div>

        <div className="resumen-reserva resumen-reserva-contenedor">
          <div className="resumen-izquierda">
            <div>
              <span>Fecha:</span>
              <strong>{reservaCreada.fecha}</strong>
            </div>
            <div>
              <span>Horario:</span>
              <strong>
                {pad(reservaCreada.horaInicio)}:00 - {pad(horaFin)}:00
              </strong>
            </div>
            <div>
              <span>Total:</span>
              <strong>{formatearMoneda(reservaCreada.total)}</strong>
            </div>
          </div>
        </div>

        <div className="confirmacion-aviso">
          {pagoHecho ? '¡Pago recibido! Tu reserva quedó confirmada y la máquina está asegurada para ti.' : 'Tu reserva está pendiente de pago. Tienes 2 horas para pagarla; pasado ese tiempo se cancela y el horario queda libre. Guarda este código para identificarla.'}
        </div>

        {errorEnvio && <Alerta>{errorEnvio}</Alerta>}

        {verPago && !pagoHecho && (
          <PagoTarjeta reservaId={reservaCreada.id} total={reservaCreada.total} onPagado={() => setPagoHecho(true)} />
        )}

        <div className="acciones-centrado">
          <button type="button" onClick={pagarAhora} disabled={pagando || verPago} style={verPago ? { display: 'none' } : undefined} className="boton boton-primario">
            {pagando ? 'Procesando…' : 'Pagar ahora'}
          </button>
          <button type="button" onClick={onExito} className="boton boton-secundario">
            Volver al catálogo
          </button>
          <button type="button" onClick={onCerrar} className="boton boton-secundario">
            Cerrar
          </button>
        </div>
      </div>
    );
  }

  if (paso === 3) {
    return <Confirmacion />;
  }

  return (
    <div className="formulario-reserva">
      <div className="titulo-seccion">
        <h2 className="subtitulo">Reservar por horas</h2>
        <button type="button" onClick={onCerrar} className="boton boton-chico boton-secundario" aria-label="Cerrar">
          ✕
        </button>
      </div>

      {paso === 1 && (
        <>
          <div className="subtitulo">Paso 1: Elige día y hora</div>

          <SelectorDia />
          <CuadriculaHoras />
          <Resumen />

          {errorEnvio && <div role="alert" aria-live="assertive"><Alerta>{errorEnvio}</Alerta></div>}

          <div className="acciones-formulario">
            <button type="button" onClick={onCerrar} className="boton boton-secundario">
              Cancelar
            </button>
            <button
              type="button"
              onClick={() => {
                if (diaSeleccionado && horaSeleccionada !== null) {
                  setPaso(2);
                  setErrorEnvio('');
                } else {
                  setErrorEnvio('Debes seleccionar día y hora');
                }
              }}
              className="boton boton-primario"
              disabled={!diaSeleccionado || horaSeleccionada === null}
            >
              Continuar →
            </button>
          </div>
        </>
      )}

      {paso === 2 && (
        <>
          <div className="subtitulo">Paso 2: Datos del solicitante</div>

          <form>
            <div className={`campo ${errores.nombreSolicitante ? 'campo-error' : ''}`}>
              <label htmlFor="nombreSolicitante">Nombre o razón social *</label>
              <input
                id="nombreSolicitante"
                type="text"
                value={formulario.nombreSolicitante}
                onChange={(e) => cambiarCampo('nombreSolicitante', e.target.value)}
                placeholder="Ej. Juan Pérez"
              />
              {errores.nombreSolicitante && <span className="mensaje-error" role="alert" aria-live="polite">{errores.nombreSolicitante}</span>}
            </div>

            <div className={`campo ${errores.ruc ? 'campo-error' : ''}`}>
              <label htmlFor="tipoDoc">Comprobante *</label>
              <select id="tipoDoc" value={formulario.tipoDoc} onChange={(e) => cambiarCampo('tipoDoc', e.target.value)}>
                <option value="RUC">Factura (con RUC)</option>
                <option value="DNI">Boleta (con DNI)</option>
              </select>
              <label htmlFor="ruc">{formulario.tipoDoc === 'DNI' ? 'DNI *' : 'RUC *'}</label>
              <input
                id="ruc"
                type="text"
                value={formulario.ruc}
                onChange={(e) => cambiarCampo('ruc', e.target.value.replace(/\D/g, '').slice(0, formulario.tipoDoc === 'DNI' ? 8 : 11))}
                placeholder={formulario.tipoDoc === 'DNI' ? 'Ej. 12345678' : 'Ej. 20123456789'}
                maxLength={formulario.tipoDoc === 'DNI' ? 8 : 11}
                aria-invalid={!!errores.ruc}
              />
              {errores.ruc && <span className="mensaje-error" role="alert" aria-live="polite">{errores.ruc}</span>}
            </div>

            <div className={`campo ${errores.telefono ? 'campo-error' : ''}`}>
              <label htmlFor="telefono">Celular *</label>
              <input
                id="telefono"
                type="tel"
                value={formulario.telefono}
                onChange={(e) => cambiarCampo('telefono', e.target.value)}
                placeholder="Ej. 987 654 321"
                aria-invalid={!!errores.telefono}
              />
              {errores.telefono && <span className="mensaje-error" role="alert" aria-live="polite">{errores.telefono}</span>}
            </div>

            <div className={`campo ${errores.email ? 'campo-error' : ''}`}>
              <label htmlFor="email">Email *</label>
              <input
                id="email"
                type="email"
                value={formulario.email}
                onChange={(e) => cambiarCampo('email', e.target.value)}
                placeholder="Ej. juan@example.com"
                aria-invalid={!!errores.email}
              />
              {errores.email && <span className="mensaje-error" role="alert" aria-live="polite">{errores.email}</span>}
            </div>

            <div className={`campo ${errores.ubicacionObra ? 'campo-error' : ''}`}>
              <label htmlFor="ubicacionObra">Ubicación de la obra *</label>
              <input
                id="ubicacionObra"
                type="text"
                value={formulario.ubicacionObra}
                onChange={(e) => cambiarCampo('ubicacionObra', e.target.value)}
                placeholder="Ej. Av. Principal 123, Lima"
                maxLength="200"
                aria-invalid={!!errores.ubicacionObra}
              />
              {errores.ubicacionObra && <span className="mensaje-error" role="alert" aria-live="polite">{errores.ubicacionObra}</span>}
            </div>

            <div className={`campo ${errores.detalle ? 'campo-error' : ''}`}>
              <label htmlFor="detalle">
                Detalle adicional ({formulario.detalle.length}/300)
              </label>
              <textarea
                id="detalle"
                value={formulario.detalle}
                onChange={(e) => cambiarCampo('detalle', e.target.value.slice(0, 300))}
                placeholder="Información adicional (opcional)"
                rows="3"
                aria-invalid={!!errores.detalle}
              />
              {errores.detalle && <span className="mensaje-error" role="alert" aria-live="polite">{errores.detalle}</span>}
            </div>

            <div className={`casilla ${errores.aceptaPolitica ? 'campo-error' : ''}`}>
              <input
                id="aceptaPolitica"
                type="checkbox"
                checked={formulario.aceptaPolitica}
                onChange={(e) => cambiarCampo('aceptaPolitica', e.target.checked)}
                aria-invalid={!!errores.aceptaPolitica}
              />
              <label htmlFor="aceptaPolitica" className="etiqueta-aceptar">
                Acepto los{' '}
                <button
                  type="button"
                  className="enlace-terminos"
                  onClick={(e) => {
                    e.preventDefault();
                    setVerTerminos(true);
                  }}
                >
                  términos y condiciones
                </button>{' '}
                y la política de privacidad *
              </label>
              {errores.aceptaPolitica && <span className="mensaje-error error-aceptar" role="alert" aria-live="polite">{errores.aceptaPolitica}</span>}
            </div>

            {errorEnvio && <div role="alert" aria-live="assertive"><Alerta>{errorEnvio}</Alerta></div>}

            <div className="acciones-formulario">
              <button
                type="button"
                onClick={() => {
                  setPaso(1);
                  setErrorEnvio('');
                }}
                className="boton boton-secundario"
                disabled={enviando}
              >
                ← Volver
              </button>
              <button
                type="button"
                onClick={enviarReserva}
                className="boton boton-primario"
                disabled={enviando}
              >
                {enviando ? 'Enviando...' : 'Confirmar reserva'}
              </button>
            </div>
          </form>
        </>
      )}

      {verTerminos && createPortal(
        <div
          className="terminos-overlay"
          onClick={() => setVerTerminos(false)}
          role="dialog"
          aria-modal="true"
          aria-labelledby="terminos-titulo"
        >
          <div className="terminos-caja" onClick={(e) => e.stopPropagation()}>
            <h2 id="terminos-titulo" className="subtitulo">Términos y condiciones</h2>
            <div className="terminos-texto">{TEXTO_TERMINOS.trim()}</div>
            <div className="acciones-formulario">
              <button type="button" className="boton boton-secundario" onClick={() => setVerTerminos(false)}>
                Cerrar
              </button>
              <button
                type="button"
                className="boton boton-primario"
                onClick={() => {
                  cambiarCampo('aceptaPolitica', true);
                  setVerTerminos(false);
                }}
              >
                Acepto
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
