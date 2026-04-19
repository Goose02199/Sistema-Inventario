package common;

public class JsonUtil {

    /**
     * Extrae el valor de texto (String) asociado a una llave en un JSON simple.
     * Ejemplo: extractString("{\"tipo\":\"VENTA\"}", "tipo") devuelve "VENTA".
     */
    public static String extractString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos < 0) return null;
        
        int colon = json.indexOf(":", keyPos);
        int firstQuote = json.indexOf("\"", colon + 1);
        int secondQuote = json.indexOf("\"", firstQuote + 1);
        
        if (colon < 0 || firstQuote < 0 || secondQuote < 0) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }

    /**
     * Extrae el valor numérico entero (int) asociado a una llave en un JSON simple.
     * Ejemplo: extractInt("{\"cantidad\":2}", "cantidad") devuelve 2.
     */
    public static Integer extractInt(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos < 0) return null;
        
        int colon = json.indexOf(":", keyPos);
        if (colon < 0) return null;
        
        int start = colon + 1;
        // Avanza espacios en blanco después de los dos puntos
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null; 
        }
    }

    /**
     * Convierte una cadena JSON plana de vuelta a nuestro objeto Java 'Transaccion'
     */
    public static Transaccion parseTransaccion(String json) {
        String id = extractString(json, "id");
        String producto = extractString(json, "producto");
        Integer cantidad = extractInt(json, "cantidad");
        String tipo = extractString(json, "tipo");
        String cliente = extractString(json, "cliente");
        
        return new Transaccion(id, producto, cantidad != null ? cantidad : 0, tipo, cliente);
    }

    /**
     * Genera un JSON simple para enviar respuestas exitosas ("OK") al cliente.
     */
    public static String ok(String mensaje) {
        return "{\n  \"status\": \"OK\",\n  \"message\": \"" + mensaje + "\"\n}";
    }

    /**
     * Genera un JSON simple para enviar respuestas de error al cliente.
     */
    public static String error(String mensaje) {
         return "{\n  \"status\": \"ERROR\",\n  \"message\": \"" + mensaje + "\"\n}";
    }
}