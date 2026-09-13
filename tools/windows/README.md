# 🪟 nOpenNow — Herramientas para Windows (GeForce NOW)

Lanzador de navegador web de escritorio optimizado para ejecutarse en la instancia de Windows de NVIDIA GeForce NOW, con almacenamiento directo en el **disco `I:\`**.

---

## 🎯 ¿Cómo funciona?

En las máquinas virtuales de GeForce NOW, el almacenamiento del usuario y juegos se encuentra en la unidad **`I:\`**.

El script **`nOpenNow-Browser.bat`** es un ejecutable híbrido (.bat + PowerShell integrado):
1. **Detecta el disco `I:\`** y crea automáticamente la carpeta **`I:\nOpenNow_Browser\`** con subdirectorios para perfil, caché y descargas (`I:\nOpenNow_Browser\Profile`, `I:\nOpenNow_Browser\Cache`, `I:\nOpenNow_Browser\Downloads`).
2. **Localiza el navegador de Windows (Edge o Chrome)** o descarga uno portable independiente dentro de `I:\nOpenNow_Browser\`.
3. **Lanza el navegador con aceleración GPU RTX completa a 60 FPS**, maximizado y sin diálogos de bienvenida.

---

## 📥 Enlace de Descarga Directa

Puedes descargar el archivo `.bat` directamente en la instancia de Windows con este enlace:

👉 **[Descargar nOpenNow-Browser.bat](https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/nOpenNow-Browser.bat)**

---

## 🚀 Instrucciones (2 clics)

1. En la sesión de Windows en GFN, abre el navegador de Steam (`Shift + Tab`) o un navegador existente.
2. Descarga `nOpenNow-Browser.bat` desde el enlace.
3. **Haz doble clic en `nOpenNow-Browser.bat`.**
4. Detecta automáticamente **PowerShell 7** de SalsaNOW (`I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe`) para evitar las restricciones del PowerShell del sistema, creará todo en el disco `I:\nOpenNow_Browser` y abrirá el navegador al instante en pantalla completa.
