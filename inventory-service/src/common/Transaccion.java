package common;
import java.io.Serializable;

public class Transaccion implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id;
    public String producto;
    public int cantidad;
    public String tipo; 
    public String cliente;

    public Transaccion(String id, String producto, int cantidad, String tipo, String cliente) {
        this.id = id;
        this.producto = producto;
        this.cantidad = cantidad;
        this.tipo = tipo;
        this.cliente = cliente;
    }
}