# proyecto_pagina_Tienda
.

## Publicar imágenes en Docker Hub

El workflow `.github/workflows/docker-publish.yml` construye y publica ambas
imágenes con cada push a `Develop` o `main`. También permite ejecución manual
desde GitHub Actions cuando el workflow está en la rama predeterminada.

Utiliza los secretos de Actions `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN`
(token de Docker Hub con permiso de escritura). Si los secretos existentes
tienen otros nombres, cambia las referencias en el workflow.

Imágenes publicadas:

- `<usuario>/carnivario-backend:latest`
- `<usuario>/carnivario-frontend:latest`

También se publica una etiqueta con el SHA del commit. `latest` corresponde
a la última publicación, sea de `Develop` o de `main`.

No requiere Docker Compose. El backend escucha en el puerto 8081 y el frontend
en el 80. La compilación del backend omite tests; al ejecutar el contenedor
debes proporcionar las variables de base de datos, JWT, Azure y correo de
`backend/src/main/resources/application.yml`. No se incluyen archivos `.env`
en las imágenes. La URL de API del frontend sigue definida en
`frontend/src/api/axiosConfig.js`.

# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.
