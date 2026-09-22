// Calendario de disponibilidad (base para HU-04 criterio 2): días libres y ocupados
import { hoyISO } from '../utils/formato';

const DIAS_SEMANA = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
const pad = (n) => String(n).padStart(2, '0');

function nombreMes(anio, mes) {
  const mesTexto = new Intl.DateTimeFormat('es-PE', { month: 'long' }).format(new Date(anio, mes, 1));
  return `${mesTexto.charAt(0).toUpperCase()}${mesTexto.slice(1)} ${anio}`;
}

export default function Calendario({ ocupados = [], meses = 2 }) {
  const hoy = hoyISO();
  const [anioHoy, mesHoy] = hoy.split('-').map(Number);
  const estaOcupado = (iso) => ocupados.some((o) => o.fechaInicio <= iso && iso <= o.fechaFin);

  const listaMeses = Array.from({ length: meses }, (_, i) => {
    const fecha = new Date(anioHoy, mesHoy - 1 + i, 1);
    return { anio: fecha.getFullYear(), mes: fecha.getMonth() };
  });

  return (
    <>
      <div className="calendarios">
        {listaMeses.map(({ anio, mes }) => {
          const primerDia = (new Date(anio, mes, 1).getDay() + 6) % 7; // lunes = 0
          const diasEnMes = new Date(anio, mes + 1, 0).getDate();
          return (
            <div className="calendario" key={`${anio}-${mes}`}>
              <div className="cal-titulo">{nombreMes(anio, mes)}</div>
              <div className="cal-rejilla">
                {DIAS_SEMANA.map((d, i) => (
                  <div className="cal-semana" key={i}>{d}</div>
                ))}
                {Array.from({ length: primerDia }, (_, i) => (
                  <div className="cal-vacio" key={`v${i}`} />
                ))}
                {Array.from({ length: diasEnMes }, (_, i) => {
                  const dia = i + 1;
                  const iso = `${anio}-${pad(mes + 1)}-${pad(dia)}`;
                  const clases = ['cal-dia'];
                  if (iso < hoy) clases.push('pasado');
                  else if (estaOcupado(iso)) clases.push('ocupado');
                  if (iso === hoy) clases.push('hoy');
                  return (
                    <div className={clases.join(' ')} key={iso} title={estaOcupado(iso) ? 'No disponible' : undefined}>
                      {dia}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
      <div className="leyenda">
        <span><i className="l-libre" />Disponible</span>
        <span><i className="l-ocupado" />No disponible</span>
        <span><i className="l-hoy" />Hoy</span>
      </div>
    </>
  );
}
