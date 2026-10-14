package alquiler;

import alquiler.util.ValidadorRuc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidadorRuc")
class ValidadorRucTest {

    // RUC válidos: calculados con el algoritmo correcto (pesos [5,4,3,2,7,6,5,4,3,2])
    private static final String RUC_VALIDO_1 = "20000000109";  // Prefijo 20
    private static final String RUC_VALIDO_2 = "10000000103";  // Prefijo 10
    private static final String RUC_VALIDO_3 = "15000000105";  // Prefijo 15

    @Test
    @DisplayName("RUC válido con prefijo 20")
    void esValidoConPrefijo20() {
        assertTrue(ValidadorRuc.esValido(RUC_VALIDO_1));
    }

    @Test
    @DisplayName("RUC válido con prefijo 10")
    void esValidoConPrefijo10() {
        assertTrue(ValidadorRuc.esValido(RUC_VALIDO_2));
    }

    @Test
    @DisplayName("RUC válido con prefijo 15")
    void esValidoConPrefijo15() {
        assertTrue(ValidadorRuc.esValido(RUC_VALIDO_3));
    }

    @Test
    @DisplayName("RUC inválido: prefijo incorrecto (11)")
    void esInvalidoConPrefijo11() {
        assertFalse(ValidadorRuc.esValido("11123456789"));
    }

    @Test
    @DisplayName("RUC inválido: prefijo incorrecto (99)")
    void esInvalidoConPrefijo99() {
        assertFalse(ValidadorRuc.esValido("99123456789"));
    }

    @Test
    @DisplayName("RUC inválido: menos de 11 dígitos")
    void esInvalidoMenosDe11Digitos() {
        assertFalse(ValidadorRuc.esValido("2060704213"));
    }

    @Test
    @DisplayName("RUC inválido: más de 11 dígitos")
    void esInvalidoMasDe11Digitos() {
        assertFalse(ValidadorRuc.esValido("206070421388"));
    }

    @Test
    @DisplayName("RUC inválido: contiene letras")
    void esInvalidoConLetras() {
        assertFalse(ValidadorRuc.esValido("2060704213A"));
    }

    @Test
    @DisplayName("RUC inválido: dígito verificador incorrecto")
    void esInvalidoConVerificadorIncorrecto() {
        assertFalse(ValidadorRuc.esValido("20607042139"));  // Cambié el último dígito
    }

    @Test
    @DisplayName("RUC inválido: null")
    void esInvalidoNull() {
        assertFalse(ValidadorRuc.esValido(null));
    }

    @Test
    @DisplayName("RUC inválido: cadena vacía")
    void esInvalidoVacio() {
        assertFalse(ValidadorRuc.esValido(""));
    }

    @Test
    @DisplayName("RUC inválido: prefijo 17")
    void esValidoConPrefijo17() {
        // Generar un RUC válido con prefijo 17
        String ruc = generarRucValido("17");
        assertTrue(ValidadorRuc.esValido(ruc));
    }

    /**
     * Genera un RUC válido con el prefijo especificado.
     * Calcula el dígito verificador con el algoritmo correcto.
     */
    private String generarRucValido(String prefijo) {
        // Base: prefijo + 8 dígitos (total 10) + dígito verificador = 11
        String base = prefijo + "00000001";  // 2 + 8 = 10 dígitos para calcular verificador
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

        int suma = 0;
        for (int i = 0; i < 10; i++) {
            int digito = Character.getNumericValue(base.charAt(i));
            suma += digito * pesos[i];
        }

        int resto = suma % 11;
        int verificador = 11 - resto;
        if (verificador == 10) {
            verificador = 0;
        } else if (verificador == 11) {
            verificador = 1;
        }

        return base + verificador;
    }
}
