package server;

import common.*;
import com.sun.net.httpserver.*; 
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.net.http.*;

public class InventoryServer {
    private int id;
    private int puertoPublico;
    private int puertoCluster;
    private int idLider = 3; 
    private boolean esLider;
    private long proximaSecuencia = 1;
    private long ultimaSenalLider = System.currentTimeMillis();
    private static final int LEADER_TIMEOUT = 5000;

    private ServerSocket serverCluster;
    private HttpServer servidorWeb; 

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10); 
    private static final ConcurrentHashMap<String, Integer> inventario = new ConcurrentHashMap<>(); 
    private static final Map<Integer, Integer> PEERS = new HashMap<>();

    public InventoryServer(int id, int puertoP, int puertoC) {
        this.id = id;
        this.puertoPublico = puertoP;
        this.puertoCluster = puertoC;
        this.esLider = (id == idLider);
        
        PEERS.put(1, 6001);
        PEERS.put(2, 6002);
        PEERS.put(3, 6003);

        inventario.put("Laptop", 50);
        inventario.put("Mouse", 100);
    }

    public void iniciar() throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SISTEMA] Cerrando servidor web y sockets...");
            if (servidorWeb != null) servidorWeb.stop(0);
            try {
                if (serverCluster != null && !serverCluster.isClosed()) serverCluster.close();
            } catch (IOException e) {}
            threadPool.shutdown(); 
            System.out.println("[SISTEMA] Recursos liberados correctamente.");
        }));

        System.out.println("[Servidor " + id + "] Iniciado. Esperando líder: " + idLider);
        
        Thread tCluster = new Thread(this::escucharCluster);
        tCluster.setDaemon(true);
        tCluster.start();

        Thread tMonitor = new Thread(this::monitorearLider);
        tMonitor.setDaemon(true);
        tMonitor.start();

        servidorWeb = HttpServer.create(new InetSocketAddress(puertoPublico), 0);
        
        servidorWeb.createContext("/api/transaccion", new TransaccionHandler());
        servidorWeb.createContext("/api/inventario", new InventarioHandler()); // <-- NUEVO ENDPOINT GET
        
        servidorWeb.setExecutor(threadPool); 
        servidorWeb.start();
        
        System.out.println("[HTTP] API REST lista en http://localhost:" + puertoPublico + "/api/transaccion");
        System.out.println("[HTTP] Consulta de estado en http://localhost:" + puertoPublico + "/api/inventario");
    }

    private void notificarFacturacion(Transaccion t) {
        threadPool.execute(() -> {
            try {
                System.out.println("[INTER-SERVICE] Notificando al Microservicio de Facturación (Python)...");
                
                double precioUnitario = t.producto.equalsIgnoreCase("Laptop") ? 15000.0 : 250.0;
                double totalPagado = t.cantidad * precioUnitario;

                String jsonFactura = String.format(Locale.US,
                    "{\"cliente\": \"%s\", \"producto\": \"%s\", \"cantidad\": %d, \"total_pagado\": %.2f}",
                    t.cliente, t.producto, t.cantidad, totalPagado
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://billing-service:8000/api/facturacion"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonFactura, StandardCharsets.UTF_8))
                    .build();

                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    System.out.println("[INTER-SERVICE] ÉXITO: Factura registrada en PostgreSQL. Respuesta: " + response.body());
                } else {
                    System.out.println("[INTER-SERVICE ERROR] Código HTTP devuelto: " + response.statusCode());
                }
            } catch (Exception e) {
                System.out.println("[INTER-SERVICE ERROR] Falló la red hacia Python: " + e.getMessage());
            }
        });
    }

    class InventarioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

            System.out.println("\n=================================================");
            System.out.println("[HTTP GATEWAY] Consulta GET recibida");
            System.out.println("[METADATA] Método: " + method + " | URI: " + uri + " | Origen: " + clientIp);

            if (!"GET".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1); 
                return;
            }

            // Construir el JSON de respuesta manualmente (para no usar librerías externas)
            int stockLaptop = inventario.getOrDefault("Laptop", 0);
            int stockMouse = inventario.getOrDefault("Mouse", 0);
            
            String jsonRespuesta = String.format(
                "{\n  \"lider_actual\": %d,\n  \"inventario\": {\n    \"Laptop\": %d,\n    \"Mouse\": %d\n  }\n}",
                idLider, stockLaptop, stockMouse
            );

            byte[] responseBytes = jsonRespuesta.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
            System.out.println("[RESPONSE] Estado del inventario enviado.");
            System.out.println("=================================================\n");
        }
    }

    class TransaccionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String uri = exchange.getRequestURI().toString();
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

            System.out.println("\n=================================================");
            System.out.println("[HTTP GATEWAY] Nueva petición entrante interceptada");
            System.out.println("[METADATA] Método: " + method + " | URI: " + uri + " | Origen: " + clientIp);

            if (!"POST".equalsIgnoreCase(method)) {
                System.out.println("[ERROR] Método " + method + " no soportado. Respondiendo HTTP 405 (Method Not Allowed).");
                System.out.println("=================================================\n");
                exchange.sendResponseHeaders(405, -1); 
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            Transaccion t = JsonUtil.parseTransaccion(body);
            System.out.println("[PAYLOAD] JSON descifrado -> Cliente: " + t.cliente + " | Acción: " + t.tipo + " | Prod: " + t.producto + " | Cant: " + t.cantidad);

            String respuesta;
            if (esLider) {
                boolean exito = procesarYReplicar(t);

                if (exito && t.tipo.equals("VENTA")) {
                    notificarFacturacion(t);
                }

                respuesta = exito ? "OK: Transacción procesada por el Líder" : "RECHAZADO: Stock insuficiente para " + t.producto;
                System.out.println("[RESOLUCIÓN] Ejecutado localmente (Nodo Líder). Resultado: " + (exito ? "ÉXITO" : "FALLO"));
            } else {
                reenviarAlLider(new Mensaje("REENVIO_TRANSACCION", t));
                respuesta = "OK: Transacción reenviada al Líder para su procesamiento";
                System.out.println("[RESOLUCIÓN] Delegado al líder actual (" + idLider + ") mediante clúster TCP.");
            }

            String jsonRespuesta = JsonUtil.ok(respuesta);
            byte[] responseBytes = jsonRespuesta.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
            System.out.println("[RESPONSE] Payload JSON devuelto. Código HTTP 200 OK enviado al cliente.");
            System.out.println("=================================================\n");
        }
    }

    private void escucharCluster() {
        try {
            this.serverCluster = new ServerSocket();
            this.serverCluster.setReuseAddress(true);
            this.serverCluster.bind(new InetSocketAddress(puertoCluster));
            
            System.out.println("[Cluster] Escuchando en puerto " + puertoCluster);
            while (!serverCluster.isClosed()) {
                Socket peerSocket = serverCluster.accept();
                threadPool.execute(() -> {
                    try (ObjectOutputStream out = new ObjectOutputStream(peerSocket.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(peerSocket.getInputStream())) {
                        
                        Mensaje m = (Mensaje) in.readObject();
                        
                        out.writeObject(new Mensaje("ACK", "Recibido"));
                        out.flush();
                        
                        if (m.getTipo().equals("EVENTO_REPLICADO")) {
                            aplicarCambio((EventoReplicado) m.getContenido()); 
                        } else if (m.getTipo().equals("REENVIO_TRANSACCION") && esLider) {
                            procesarYReplicar((Transaccion) m.getContenido());
                        } else if (m.getTipo().equals("LEADER_HEARTBEAT")) {
                            int idRemitente = (int) m.getContenido();
                            this.ultimaSenalLider = System.currentTimeMillis();
                            
                            if (idRemitente != this.idLider) {
                                this.idLider = idRemitente;
                                this.esLider = (this.id == this.idLider);
                                System.out.println("[CLUSTER] Líder actualizado vía Heartbeat: " + idLider);
                            } 
                        } else if (m.getTipo().equals("NUEVO_LIDER")) {
                            this.idLider = (int) m.getContenido();
                            this.esLider = (id == idLider);
                            this.ultimaSenalLider = System.currentTimeMillis();
                            System.out.println("[CLUSTER] Nuevo líder reconocido: " + idLider);
                        } else if (m.getTipo().equals("ELECTION")) {
                            new Thread(this::iniciarEleccion).start();
                        }
                    } catch (Exception e) { }
                });
            }
        } catch (IOException e) {
            if (serverCluster != null && !serverCluster.isClosed()) e.printStackTrace();
        }
    }

    private void difundirAlCluster(Mensaje m) { 
        for (Map.Entry<Integer, Integer> peer : PEERS.entrySet()) {
            if (peer.getKey() != this.id) { 
                enviarAMensajePeer(peer.getValue(), m);
            }
        }
    }

    private void reenviarAlLider(Mensaje msg) {
        Integer puertoLider = PEERS.get(idLider);
        if (puertoLider != null) {
            enviarAMensajePeer(puertoLider, new Mensaje("REENVIO_TRANSACCION", msg.getContenido()));
        }
    }

    private void enviarAMensajePeer(int puerto, Mensaje m) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", puerto), 1000);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                
                out.writeObject(m);
                out.flush();
                in.readObject(); 
            }
        } catch (Exception e) { }
    }

    private synchronized boolean procesarYReplicar(Transaccion t) {
        int stockActual = inventario.getOrDefault(t.producto, 0);
        boolean operacionValida = false;

        if (t.tipo.equals("VENTA") && stockActual >= t.cantidad) {
            operacionValida = true;
        } else if (t.tipo.equals("COMPRA")) {
            operacionValida = true; 
        }

        if (operacionValida) {
            EventoReplicado evento = new EventoReplicado(proximaSecuencia++, t.tipo, t.producto, t.cantidad);
            aplicarCambio(evento);
            difundirAlCluster(new Mensaje("EVENTO_REPLICADO", evento)); 
            return true;
        } else {
            System.out.println("[LÍDER] Transacción rechazada: " + t.tipo + " de " + t.producto + " (Stock insuficiente)");
            return false;
        }
    }

    private void aplicarCambio(EventoReplicado e) {
        synchronized (inventario) {
            int actual = inventario.getOrDefault(e.producto, 0);
            if (e.operacion.equals("VENTA")) {
                inventario.put(e.producto, actual - e.cantidad);
            } else {
                inventario.put(e.producto, actual + e.cantidad);
            }
            System.out.println("[STOCK] " + e.producto + " actualizado a: " + inventario.get(e.producto));
        }
    }

    private void monitorearLider() {
        while (true) {
            try {
                if (!esLider) {
                    if (this.id > this.idLider) {
                        System.out.println("[BULLY] Reclamando mando al líder menor (" + idLider + ")...");
                        iniciarEleccion();
                    }
                    
                    if (System.currentTimeMillis() - ultimaSenalLider > LEADER_TIMEOUT) {
                        System.out.println("[ALERTA] Líder fuera de línea. Iniciando elección...");
                        iniciarEleccion();
                    }
                } else {
                    difundirAlCluster(new Mensaje("LEADER_HEARTBEAT", this.id));
                }
                Thread.sleep(3000); 
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private synchronized void iniciarEleccion() {
        boolean alguienMayorResponde = false;
        System.out.println("[ELECTION] Iniciada por servidor " + id);

        for (Integer peerId : PEERS.keySet()) {
            if (peerId > this.id) { 
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress("localhost", PEERS.get(peerId)), 1000);
                    try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                         ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                        
                        out.writeObject(new Mensaje("ELECTION", id));
                        out.flush();
                        
                        Mensaje ack = (Mensaje) in.readObject();
                        if (ack != null) alguienMayorResponde = true; 
                    }
                } catch (Exception e) { }
            }
        }

        if (!alguienMayorResponde) {
            convertirseEnLider(); 
        }
    }

    private void convertirseEnLider() {
        this.esLider = true;
        this.idLider = this.id;
        System.out.println("[COORDINADOR] Ahora yo soy el líder: " + id);
        difundirAlCluster(new Mensaje("NUEVO_LIDER", id));
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 3) {
            int id = Integer.parseInt(args[0]);
            int pPub = Integer.parseInt(args[1]);
            int pClust = Integer.parseInt(args[2]);
            new InventoryServer(id, pPub, pClust).iniciar();
        } else {
            new InventoryServer(1, 5001, 6001).iniciar();
        }
    }
}