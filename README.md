# NavegadorAula 🛡️🎓

<p align="center">
  <img src="app/src/main/res/drawable/ic_logo.png" alt="NavegadorAula Logo">
</p>

**NavegadorAula** es un navegador web seguro para Android, diseñado específicamente para entornos educativos. Su objetivo principal es proveer una experiencia de navegación controlada, rápida y robusta, garantizando la seguridad de los estudiantes mediante múltiples capas de validación y prevención contra contenido explícito, redes maliciosas y software dañino.

---

## Características Principales ✨

- **Bloqueo Inteligente de Contenido Adulto:** Sistema propietario que analiza y desofusca el texto visible de la página web mediante algoritmos asíncronos y corrutinas en segundo plano (`Dispatchers.Default`).
- **Sanitización Anti-Evasión (Leetspeak):** Elimina acentos y traduce caracteres especiales (`p0rn` → `porn`, `an@l` → `anal`) antes de la validación para prevenir derivaciones por parte de los usuarios.
- **Ventanas Emergentes Nativas (Dialogs):** Interrumpe estrictamente la navegación y advierte al usuario a través de componentes modales de `MaterialAlertDialogBuilder` integrados al sistema nativo.
- **Navegación Exclusiva HTTPS:** Previene ataques Man-in-the-Middle (MitM) forzando el protocolo seguro TLS. Las peticiones a sitios HTTP o de texto plano son bloqueadas por defecto.
- **Validación con Google Safe Browsing API:** Integración directa con los sistemas de detección de Google para identificar páginas engañosas (Phishing), programas maliciosos (Malware) y software no deseado en tiempo real.
- **Privacidad y Borrado Automático:** Al presionar "Borrar" o cuando se detecta contenido inapropiado, el motor de navegación purga automáticamente la caché, las cookies, el almacenamiento local y el historial, regresando al estado inicial.
- **Modo Pantalla Completa y Diseño Material 3:** Interfaz moderna y minimalista con una experiencia de inmersión total. Ocultamiento inteligente de barras y uso de componentes `Material Design 3`.

---

## Arquitectura y Seguridad 🏗️

El navegador utiliza `Clean Architecture` y está implementado en **Kotlin** para Android, adhiriéndose a las mejores prácticas de **OWASP Mobile Security**:

1. **`UrlValidator`:** Sanitiza los campos de entrada y obliga la introducción de URLs correctas sin espacios y sobre SSL.
2. **`DomainFilter` (Blacklisting):** Mantiene una amplia lista negra descentralizada de dominios conocidos que difunden pornografía, violencia extrema o apuestas.
3. **`KeywordFilter` y `HtmlAnalyzer`:** Descarga en memoria el DOM y evalúa en un hilo IO las palabras clave tanto de las meta-etiquetas como del texto visible con diccionarios bilingües, bloqueando dinámicamente sitios explícitos que no están en la lista negra.
4. **`SecureWebViewClient`:** Intercepta la navegación y las peticiones de los sub-recursos de la web. Impide certificados SSL defectuosos abortando inmediatamente la conexión (`handler.cancel()`).

---

## Requisitos de Sistema 📱

- **Lenguaje:** Kotlin 1.9+
- **SDK Mínimo:** Android 8.0 (API 26)
- **SDK Objetivo:** Android 14 (API 34)
- **Herramientas:** Android Studio (Iguana o superior), Gradle 8.7+

---

## Instalación y Configuración 🚀

1. **Clonar Repositorio:**
   ```bash
   git clone <URL_DEL_REPOSITORIO>
   cd NavegadorAula
   ```
2. **Abrir en Android Studio:**
   - Abre Android Studio y selecciona **File > Open**.
   - Navega hasta el directorio `NavegadorAula` y ábrelo.
   - Espera a que Gradle sincronice las dependencias del proyecto.
3. **Compilación y Ejecución:**
   - Conecta tu dispositivo Android físico (con la depuración USB habilitada) o utiliza un Emulador AVD.
   - Haz clic en el botón **Run** (Play) o presiona `Shift + F10` para desplegar la APK en tu dispositivo.

---

## Consideraciones Adicionales 🔒

Al tratarse de un entorno educativo, **no se permite la apertura de sub-ventanas web (`window.open`)**. El control del usuario está fuertemente enfocado en el campo de la URL principal. Todo el contenido prohibido detectado obliga al WebView a reubicarse silenciosa y rápidamente en la página neutra (`about:blank`), preservando la limpieza visual de la aplicación.
