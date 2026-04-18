# Usa Node.js versión Alpine para mayor ligereza
FROM node:20-alpine

WORKDIR /app

# Copiamos archivos de dependencias
COPY package.json package-lock.json* ./

# Instalamos las dependencias
RUN npm install

# Copiamos el resto del código
COPY . .

# Exponemos el puerto por defecto de Vite
EXPOSE 5173

# IMPORTANTE: --host es necesario para que el contenedor acepte conexiones externas (de NGINX)
CMD ["npm", "run", "dev", "--", "--host"]