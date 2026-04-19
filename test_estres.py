import urllib.request
import json
import concurrent.futures
import time
import random

URL = "http://localhost/api/transaccion"

# Parámetros de la prueba de estrés
TOTAL_PETICIONES = 50
CONCURRENCIA = 10  # Cuántos hilos disparan al mismo tiempo

def enviar_transaccion(i):
    # payload simulando ventas rápidas de Laptops
    payload = {
        "id": f"TRX-STRESS-{i}",
        "producto": "Laptop",
        "cantidad": 1,
        "tipo": "VENTA",
        "cliente": f"Bot_Estrés_{i}"
    }
    
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(URL, data=data, headers={"Content-Type": "application/json"}, method="POST")
    
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode("utf-8")
            return f"Hilo {i:02d} -> HTTP {response.status}: {res_body}"
    except urllib.error.URLError as e:
        return f"Hilo {i:02d} -> ERROR: {e.reason}"

def iniciar_prueba():
    print(f"Iniciando prueba de estrés: {TOTAL_PETICIONES} peticiones concurrentes...")
    inicio = time.time()
    
    exitos = 0
    # ThreadPool para simular múltiples clientes golpeando el Gateway al mismo tiempo
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCIA) as executor:
        futuros = {executor.submit(enviar_transaccion, i): i for i in range(1, TOTAL_PETICIONES + 1)}
        
        for futuro in concurrent.futures.as_completed(futuros):
            resultado = futuro.result()
            print(resultado)
            if "OK" in resultado:
                exitos += 1

    fin = time.time()
    print("-" * 50)
    print(f"Tiempo total: {fin - inicio:.2f} segundos")
    print(f"Tasa de éxito de peticiones HTTP: {exitos}/{TOTAL_PETICIONES}")
    print("Revisa tu Dashboard web para confirmar que se descontó el stock y se generaron las facturas.")

if __name__ == "__main__":
    iniciar_prueba()