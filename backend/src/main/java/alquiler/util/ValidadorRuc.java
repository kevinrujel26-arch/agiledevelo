package alquiler.util;

/** Valida números de RUC peruano. */
public class ValidadorRuc {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final String[] PREFIJOS_VALIDOS = {"10", "15", "17", "20"};

    /**
     * Valida un RUC peruano.
     * - 11 dígitos numéricos
     * - Prefijo (primeros 2 dígitos): 10, 15, 17 o 20
     * - Dígito verificador (posición 11): módulo 11 con pesos [5,4,3,2,7,6,5,4,3,2]
     *   sobre los 10 primeros dígitos.
     *
     * @param ruc el RUC a validar
     * @return true si el RUC es válido, false en caso contrario
     */
    public static boolean esValido(String ruc) {
        if (ruc == null || ruc.isEmpty()) {
            return false;
        }

        // Debe tener exactamente 11 caracteres y todos ser dígitos
        if (ruc.length() != 11 || !ruc.matches("\\d{11}")) {
            return false;
        }

        // Validar prefijo
        String prefijo = ruc.substring(0, 2);
        boolean prefijoCorrecto = false;
        for (String p : PREFIJOS_VALIDOS) {
            if (prefijo.equals(p)) {
                prefijoCorrecto = true;
                break;
            }
        }
        if (!prefijoCorrecto) {
            return false;
        }

        // Calcular dígito verificador
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            int digito = Character.getNumericValue(ruc.charAt(i));
            suma += digito * PESOS[i];
        }

        int resto = suma % 11;
        int verificador = 11 - resto;

        // Corregir según reglas especiales
        if (verificador == 10) {
            verificador = 0;
        } else if (verificador == 11) {
            verificador = 1;
        }

        // Comparar con el dígito 11 (índice 10)
        int digitoFinal = Character.getNumericValue(ruc.charAt(10));
        return verificador == digitoFinal;
    }
}
