// Validador de RUC peruano (módulo 11)
// 11 dígitos, empieza con 10, 15, 17 o 20, pesos [5,4,3,2,7,6,5,4,3,2], dígito = 11 - (suma % 11)
// 10->0, 11->1

export function validarRuc(ruc) {
  const texto = (ruc || '').trim();

  // Validaciones básicas
  if (!texto) return 'El RUC es obligatorio';
  if (!/^\d{11}$/.test(texto)) return 'El RUC debe tener 11 dígitos';

  const prefijo = texto.substring(0, 2);
  if (!['10', '15', '17', '20'].includes(prefijo)) {
    return 'El RUC debe empezar con 10, 15, 17 o 20';
  }

  // Cálculo del dígito verificador (módulo 11)
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let suma = 0;

  for (let i = 0; i < 10; i++) {
    suma += parseInt(texto[i], 10) * pesos[i];
  }

  let digito = 11 - (suma % 11);
  if (digito === 10) digito = 0;
  else if (digito === 11) digito = 1;

  const digitoEsperado = parseInt(texto[10], 10);
  if (digito !== digitoEsperado) {
    return 'El RUC ingresado no es válido';
  }

  return '';
}

export function validarDni(dni) {
  const texto = (dni || '').trim();
  if (!texto) return 'El DNI es obligatorio';
  if (!/^\d{8}$/.test(texto)) return 'El DNI debe tener 8 d\u00edgitos';
  return '';
}