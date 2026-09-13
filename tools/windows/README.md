# 🪟 nOpenNow — Herramientas para Windows (GeForce NOW)

Este módulo contiene scripts diseñados para ejecutarse **dentro de las instancias virtuales de Windows en NVIDIA GeForce NOW**.

---

## 🎯 ¿Para qué sirve?
A diferencia de un VPS Linux tradicional, **GeForce NOW** ejecuta instancias de Windows de alto rendimiento con GPUs NVIDIA RTX y red de bajísima latencia. 

Mediante estos scripts, puedes ejecutar un navegador web completo (Edge, Chrome o portable) directamente en el escritorio de Windows de GeForce NOW y controlarlo desde la aplicación móvil **nOpenNow** en Android.

---

## 🚀 Cómo ejecutarlo en la instancia de Windows (en 3 pasos)

1. **Abre la sesión en GeForce NOW desde nOpenNow en tu celular.**
2. **Accede a la interfaz de Windows:**
   - Si iniciaste un juego en Steam, presiona `Shift + Tab` (o abre el menú de Steam en pantalla) y selecciona el **Navegador web de Steam**.
   - O abre cualquier ventana con enlace web o diálogo de ejecución (`Win + R`).
3. **Ejecuta el siguiente comando en PowerShell:**
   ```powershell
   irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/browser.ps1 | iex
   ```
   *(O descarga y ejecuta `launch-browser.bat`).*

---

## ⚙️ Características del Lanzador
- **Cero Privilegios de Admin:** No requiere permisos de administrador; todo se ejecuta en `%TEMP%`.
- **Aceleración GPU RTX Forzada:** Configura flags para renderizado por hardware, cero copias de texturas y 60 FPS nativos.
- **Perfil Aislado:** Evita bloqueos de sesión y no deja rastros en la máquina virtual.
