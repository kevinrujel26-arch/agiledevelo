// Pruebas unitarias (no usan la base de datos)
const { esFechaValida, rangosSeSuperponen, buscarSuperposicion, diasEntre } = require('../src/utils/fechas');

describe('utilidades de fechas', () => {
  test('esFechaValida acepta fechas reales y rechaza inválidas', () => {
    expect(esFechaValida('2026-02-28')).toBe(true);
    expect(esFechaValida('2026-02-30')).toBe(false);
    expect(esFechaValida('28/02/2026')).toBe(false);
    expect(esFechaValida(undefined)).toBe(false);
  });

  test('rangos inclusivos: compartir un solo día es superposición', () => {
    const a = { fechaInicio: '2026-10-01', fechaFin: '2026-10-05' };
    expect(rangosSeSuperponen(a, { fechaInicio: '2026-10-05', fechaFin: '2026-10-07' })).toBe(true);
    expect(rangosSeSuperponen(a, { fechaInicio: '2026-10-06', fechaFin: '2026-10-07' })).toBe(false);
  });

  test('buscarSuperposicion detecta cruces aunque vengan desordenados', () => {
    const rangos = [
      { fechaInicio: '2026-10-20', fechaFin: '2026-10-22' },
      { fechaInicio: '2026-10-01', fechaFin: '2026-10-03' },
      { fechaInicio: '2026-10-21', fechaFin: '2026-10-25' },
    ];
    expect(buscarSuperposicion(rangos)).not.toBeNull();
    expect(buscarSuperposicion(rangos.slice(0, 2))).toBeNull();
  });

  test('diasEntre cuenta ambos extremos (mínimo 1 día)', () => {
    expect(diasEntre('2026-10-01', '2026-10-01')).toBe(1);
    expect(diasEntre('2026-10-01', '2026-10-05')).toBe(5);
    expect(diasEntre('2026-02-27', '2026-03-01')).toBe(3);
  });
});
