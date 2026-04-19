from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import psycopg2
import os
from typing import List

app = FastAPI(title="DLI Billing Service", version="1.0")

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://admin:supersecreto@localhost:5432/dli_billing")

class Factura(BaseModel):
    cliente: str
    producto: str
    cantidad: int
    total_pagado: float

def get_db_connection():
    try:
        conn = psycopg2.connect(DATABASE_URL)
        return conn
    except Exception as e:
        print(f"Error conectando a la BD: {e}")
        raise HTTPException(status_code=500, detail="Error de base de datos")

@app.on_event("startup")
def startup_event():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS facturas (
            id SERIAL PRIMARY KEY,
            cliente VARCHAR(100) NOT NULL,
            producto VARCHAR(100) NOT NULL,
            cantidad INTEGER NOT NULL,
            total_pagado DECIMAL(10, 2) NOT NULL,
            fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    ''')
    conn.commit()
    cursor.close()
    conn.close()
    print("Base de datos inicializada correctamente.")

# ENDPOINT 1: Guardar una nueva factura
@app.post("/api/facturacion")
def registrar_factura(factura: Factura):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO facturas (cliente, producto, cantidad, total_pagado) VALUES (%s, %s, %s, %s) RETURNING id;",
        (factura.cliente, factura.producto, factura.cantidad, factura.total_pagado)
    )
    nuevo_id = cursor.fetchone()[0]
    conn.commit()
    cursor.close()
    conn.close()
    
    return {"status": "OK", "message": f"Factura {nuevo_id} registrada para {factura.cliente}"}

# ENDPOINT 2: Consultar el historial de facturación
@app.get("/api/facturacion")
def obtener_facturas():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id, cliente, producto, cantidad, total_pagado, fecha FROM facturas ORDER BY fecha DESC;")
    filas = cursor.fetchall()
    cursor.close()
    conn.close()
    
    resultado = []
    for fila in filas:
        resultado.append({
            "id": fila[0],
            "cliente": fila[1],
            "producto": fila[2],
            "cantidad": fila[3],
            "total_pagado": float(fila[4]),
            "fecha": str(fila[5])
        })
    return {"total_registros": len(resultado), "facturas": resultado}