import { useState, useEffect } from 'react'
import './App.css'

function App() {
  const [inventario, setInventario] = useState({ Laptop: 0, Mouse: 0 })
  const [lider, setLider] = useState(null)
  const [mensaje, setMensaje] = useState('')
  // NUEVO: Estado para almacenar el historial de facturación de Python
  const [facturas, setFacturas] = useState([])

  // Función para consultar el inventario a Java
  const consultarInventario = async () => {
    try {
      const response = await fetch('/api/inventario')
      if (response.ok) {
        const data = await response.json()
        setInventario(data.inventario)
        setLider(data.lider_actual)
      } else {
        setMensaje('Error al consultar el inventario')
      }
    } catch (error) {
      setMensaje('Error de red al contactar a Java: ' + error.message)
    }
  }

  // NUEVO: Función para consultar la facturación a Python
  const consultarFacturacion = async () => {
    try {
      const response = await fetch('/api/facturacion')
      if (response.ok) {
        const data = await response.json()
        setFacturas(data.facturas)
      }
    } catch (error) {
      console.error('Error al contactar a Python:', error)
    }
  }

  // Función para enviar una transacción a Java
  const realizarTransaccion = async (tipo, producto, cantidad) => {
    setMensaje('Procesando...')
    const transaccion = {
      id: "TRX-" + Math.floor(Math.random() * 10000),
      producto: producto,
      cantidad: cantidad,
      tipo: tipo,
      cliente: "Sucursal_Web"
    }

    try {
      const response = await fetch('/api/transaccion', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(transaccion)
      })

      const data = await response.json()
      setMensaje(data.message || 'Transacción enviada')
      
      // Actualizamos ambos servicios para ver si hubo cambios
      refrescarTodo()
    } catch (error) {
      setMensaje('Error al procesar: ' + error.message)
    }
  }

  // Agrupamos las actualizaciones
  const refrescarTodo = () => {
    consultarInventario()
    consultarFacturacion()
  }

  // Al cargar la página, traemos datos de Java y de Python
  useEffect(() => {
    refrescarTodo()
  }, [])

  return (
    <div className="container">
      <header>
        <h1>DLI Dashboard Distribuido</h1>
        <div className="status-badge">
          Nodo Líder Actual (Java): <strong>{lider || 'Buscando...'}</strong>
        </div>
      </header>

      {mensaje && <div className="alert">{mensaje}</div>}

      <div className="grid">
        <div className="card">
          <h2>Laptops</h2>
          <p className="stock">{inventario.Laptop} en stock</p>
          <div className="actions">
            <button onClick={() => realizarTransaccion('COMPRA', 'Laptop', 5)} className="btn-buy">
              Comprar +5
            </button>
            <button onClick={() => realizarTransaccion('VENTA', 'Laptop', 1)} className="btn-sell">
              Vender -1
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Mouses</h2>
          <p className="stock">{inventario.Mouse} en stock</p>
          <div className="actions">
            <button onClick={() => realizarTransaccion('COMPRA', 'Mouse', 10)} className="btn-buy">
              Comprar +10
            </button>
            <button onClick={() => realizarTransaccion('VENTA', 'Mouse', 2)} className="btn-sell">
              Vender -2
            </button>
          </div>
        </div>
      </div>

      <button onClick={refrescarTodo} className="btn-refresh">
        Refrescar Estado Global
      </button>

      <div className="billing-section">
        <h2>Historial de Facturación (PostgreSQL)</h2>
        {facturas.length === 0 ? (
          <p className="empty-state">No hay facturas registradas aún.</p>
        ) : (
          <div className="table-responsive">
            <table className="billing-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Cliente</th>
                  <th>Producto</th>
                  <th>Cant.</th>
                  <th>Total Pagado</th>
                  <th>Fecha</th>
                </tr>
              </thead>
              <tbody>
                {facturas.map((factura) => (
                  <tr key={factura.id}>
                    <td>#{factura.id}</td>
                    <td>{factura.cliente}</td>
                    <td>{factura.producto}</td>
                    <td>{factura.cantidad}</td>
                    <td>${factura.total_pagado}</td>
                    <td>{factura.fecha.split('.')[0]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

export default App