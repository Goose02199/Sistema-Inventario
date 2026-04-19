package common;
import java.io.Serializable;

public class EventoReplicado implements Serializable {
    public long secuencia; 
    public String operacion; 
    public String producto;
    public int cantidad;

    public EventoReplicado(long secuencia, String operacion, String producto, int cantidad) {
        this.secuencia = secuencia;
        this.operacion = operacion;
        this.producto = producto;
        this.cantidad = cantidad;
    }
}