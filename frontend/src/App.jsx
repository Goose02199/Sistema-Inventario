import { useState, useEffect } from 'react'
import './App.css'

function App() {
  const [inventario, setInventario] = useState({ Laptop: 0, Mouse: 0 })
  const [lider, setLider] = useState(null)
  const [mensaje, setMensaje] = useState('')
  const [facturas, setFacturas] = useState([])
  const [cantidades, setCantidades] = useState({ Laptop: 1, Mouse: 1 })

  // Función para manejar el cambio en los inputs
  const handleCantidadChange = (producto, valor) => {
    // Evitamos números negativos o vacíos que rompan el backend
    const num = parseInt(valor, 10);
    setCantidades(prev => ({
      ...prev,
      [producto]: isNaN(num) || num < 1 ? 1 : num
    }))
  }

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

  // Función para consultar la facturación a Python
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
    setMensaje(`Procesando ${tipo} de ${cantidad} ${producto}(s)...`)
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
      refrescarTodo()
    } catch (error) {
      setMensaje('Error al procesar: ' + error.message)
    }
  }

  const refrescarTodo = () => {
    consultarInventario()
    consultarFacturacion()
  }

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
        {/* TARJETA LAPTOPS */}
        <div className="card">
          <h2>Laptops</h2>
          <p className="stock">{inventario.Laptop} en stock</p>
          
          <div className="input-group">
            <label>Cantidad:</label>
            <input 
              type="number" 
              min="1" 
              value={cantidades.Laptop} 
              onChange={(e) => handleCantidadChange('Laptop', e.target.value)}
              className="qty-input"
            />
          </div>

          <div className="actions">
            <button onClick={() => realizarTransaccion('COMPRA', 'Laptop', cantidades.Laptop)} className="btn-buy">
              Comprar
            </button>
            <button onClick={() => realizarTransaccion('VENTA', 'Laptop', cantidades.Laptop)} className="btn-sell">
              Vender
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Mouses</h2>
          <p className="stock">{inventario.Mouse} en stock</p>

          <div className="input-group">
            <label>Cantidad:</label>
            <input 
              type="number" 
              min="1" 
              value={cantidades.Mouse} 
              onChange={(e) => handleCantidadChange('Mouse', e.target.value)}
              className="qty-input"
            />
          </div>

          <div className="actions">
            <button onClick={() => realizarTransaccion('COMPRA', 'Mouse', cantidades.Mouse)} className="btn-buy">
              Comprar
            </button>
            <button onClick={() => realizarTransaccion('VENTA', 'Mouse', cantidades.Mouse)} className="btn-sell">
              Vender
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