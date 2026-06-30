# NavegadorAula 🛡️🎓

<p align="center">
  <img src="app/src/main/res/drawable/ic_logo.png" alt="NavegadorAula Logo" width="200"/>
</p>

<p align="center">
  <strong>Navegador web seguro y controlado para Android, diseñado específicamente para entornos educativos y de aprendizaje.</strong>
</p>

---

## Introducción 📖

**NavegadorAula** es un navegador web optimizado y altamente seguro para el ecosistema educativo. Su arquitectura e implementación están específicamente orientadas a proveer una experiencia de navegación controlada, rápida y robusta, garantizando la seguridad de los estudiantes mediante múltiples capas de validación y prevención activa contra contenido adulto, redes maliciosas, malware y software dañino.

El proyecto está construido nativamente en **Kotlin** y estructurado bajo los principios de **Clean Architecture**, siguiendo rigurosamente las pautas recomendadas por **OWASP Mobile Security** para mitigar la fuga de datos y el acceso no autorizado.

---

## Arquitectura de Defensa Multicapa 🏗️🛡️

El núcleo de la seguridad en **NavegadorAula** se basa en un modelo de defensa en profundidad distribuido en **9 capas de validación y control**:

```mermaid
graph TD
    A[Usuario introduce URL / Clic en Enlace] --> B{Capa 1 & 2: UrlValidator & KeywordFilter}
    B -->|Entrada Invalida o Keyword Prohibida| C[Bloqueo Inmediato + Redirección a about:blank]
    B -->|URL Sintácticamente Válida| D{Capa 3: DomainFilter en shouldOverrideUrlLoading}
    D -->|Host Bloqueado / Protocolo HTTP| E[Limpia WebView + Bloqueo de Dominio]
    D -->|Host Permitido (HTTPS)| F[Carga de Página Iniciada]
    F --> G{Capa 4: shouldInterceptRequest}
    G -->|Recurso HTTP o no permitido| H[Bloquear Recurso (Respuesta Vacía)]
    G -->|Recurso Seguro HTTPS| I[Procesar Renderizado de la Página]
    I --> J{Capa 5: Google Safe Browsing API}
    J -->|Amenaza de Seguridad Detectada| K[Forzar Back to Safety + Limpieza Completa]
    J -->|Navegación Segura| L[Página Termina Carga]
    L --> M[Capa 6 & 7: Extracción de DOM vía HtmlAnalyzer]
    M --> N{Capa 8: KeywordFilter en Hilo Dispatchers.Default}
    N -->|Coincidencia >= Umbral por Categoría| O[Capa 9: Diálogo de Alerta + Limpieza de Datos Forense + about:blank]
    N -->|Contenido Limpio| P[Añadir Historial Seguro + Renderizar Completamente]
```

### Detalle de las Capas de Seguridad

1. **Capa 1: Validación Sintáctica de Entrada ([UrlValidator.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/UrlValidator.kt))**
   - Asegura la presencia del protocolo seguro HTTPS en toda navegación.
   - Elimina imperfecciones de entrada de texto, espacios en blanco y caracteres maliciosos antes de procesar la solicitud en el motor de navegación.
2. **Capa 2: Sanitización de Texto y Anti-Ofuscación ([KeywordFilter.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/KeywordFilter.kt))**
   - Realiza una traducción heurística para neutralizar intentos de evasión por parte de los alumnos (e.g. traduce caracteres especiales de leetspeak como `p0rn` a `porn`, `@n@l` a `anal`, `$` a `s`, `1` a `i`).
   - Normaliza caracteres Unicode eliminando acentos y diacríticos para asegurar coincidencias insensibles a caracteres lingüísticos particulares.
3. **Capa 3: Interceptación del Ciclo de Vida del Enlace ([DomainFilter.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/DomainFilter.kt))**
   - Analiza e intercepta redirecciones y clics dentro de la página a través de `shouldOverrideUrlLoading` en [SecureWebViewClient.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/webview/SecureWebViewClient.kt).
   - Bloquea al instante esquemas o protocolos inseguros y no aptos para el ámbito educativo (`http`, `ftp`, `file`, `content`, `intent`, `javascript`, `data`, `blob`, `chrome`, entre otros).
4. **Capa 4: Interceptación y Bloqueo de Subrecursos Inseguros**
   - Controla dinámicamente la descarga de hojas de estilo (CSS), scripts externos (JS), imágenes, iframes y peticiones AJAX mediante `shouldInterceptRequest`.
   - Implementa de forma estricta la política contra contenido mixto (Mixed Content) denegando cualquier subrecurso no cifrado (HTTP).
5. **Capa 5: Protección Integrada con Google Safe Browsing API ([SafeBrowsingManager.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/SafeBrowsingManager.kt))**
   - Conexión nativa con los servicios de Google Safe Browsing en tiempo real.
   - Identifica y previene proactivamente el acceso a páginas de phishing, malware, software no deseado o cargos móviles fraudulentos.
   - En lugar de mostrar la pantalla roja de advertencia estándar (que los alumnos podrían omitir manualmente), el sistema fuerza de inmediato la acción `backToSafety` y aborta la sesión.
6. **Capa 6: Análisis de Metadatos y Encabezados del DOM ([HtmlAnalyzer.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/HtmlAnalyzer.kt))**
   - Cuando finaliza la carga de la página, se inyecta de forma segura un script JavaScript de solo lectura para extraer los campos del título, palabras clave meta, descripción meta y metadatos de Open Graph (`og:title`, `og:description`).
7. **Capa 7: Análisis de Texto Visible en el Body**
   - El script JS extrae las primeras 3000 palabras del texto visible renderizado en el navegador para validarlo contra las categorías del diccionario central.
8. **Capa 8: Búsqueda Asíncrona Multi-Hilo**
   - Delegación automática de las búsquedas de cadenas de texto y comparaciones semánticas complejas al despachador de segundo plano de Kotlin (`Dispatchers.Default`), asegurando que no se bloquee ni ralentice el hilo principal de la interfaz de usuario (Main Thread).
9. **Capa 9: Borrado Forense y de Privacidad Completa ([Utils.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/utils/Utils.kt))**
   - Al detectar cualquier coincidencia que supere los límites definidos, el navegador borra inmediatamente las cookies, el almacenamiento web (localStorage/sessionStorage), la caché del sistema y el historial de navegación, regresando a la UI al estado inicial neutro de `about:blank`.

---

## Mapeo del Código Fuente 🗺️

A continuación se detalla la organización de los componentes que integran NavegadorAula:

| Paquete / Archivo | Propósito y Responsabilidades principales |
| :--- | :--- |
| **`com.example.navegadoraula.data`** | **Capa de Datos y Repositorios** |
| 📄 [BlockedDomains.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/data/BlockedDomains.kt) | Conjunto inmutable de dominios prohibidos. Permite la detección automática recursiva de subdominios. |
| 📄 [BlockedKeywords.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/data/BlockedKeywords.kt) | Diccionario de términos prohibidos clasificados en 5 categorías (sexual, violencia, apuestas, drogas, malware) con umbrales mínimos de coincidencia tolerada. |
| **`com.example.navegadoraula.security`** | **Núcleo Lógico de Seguridad y Filtrado** |
| 📄 [DomainFilter.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/DomainFilter.kt) | Aplica las políticas de restricción de host y prohíbe explícitamente el uso de protocolos vulnerables. |
| 📄 [HtmlAnalyzer.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/HtmlAnalyzer.kt) | Orquesta la inyección y evaluación asíncrona de JavaScript en la página. |
| 📄 [KeywordFilter.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/KeywordFilter.kt) | Contiene la lógica de normalización de cadenas, limpieza de leetspeak y búsqueda de palabras restringidas en hilos IO/Default. |
| 📄 [SafeBrowsingManager.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/SafeBrowsingManager.kt) | Gestiona las respuestas de Google Safe Browsing y traduce los códigos de amenaza. |
| 📄 [UrlValidator.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/security/UrlValidator.kt) | Garantiza la validez sintáctica de las URLs ingresadas por teclado. |
| **`com.example.navegadoraula.webview`** | **Motor e Interfaz de Renderizado Web** |
| 📄 [SecureWebViewClient.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/webview/SecureWebViewClient.kt) | Cliente del ciclo de navegación que gestiona el bloqueo, errores de red y la interceptación de solicitudes. |
| 📄 [SecureWebChromeClient.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/webview/SecureWebChromeClient.kt) | Actualiza el estado de carga y barra de progreso reportado por el motor gráfico. |
| 📄 [WebViewManager.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/webview/WebViewManager.kt) | Configura el motor de WebView deshabilitando geolocalización, zoom invasivo, bases de datos obsoletas y almacenamiento local desprotegido. |
| **`com.example.navegadoraula.utils`** | **Utilidades y Ayudantes de la Aplicación** |
| 📄 [BrowsingHistoryManager.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/utils/BrowsingHistoryManager.kt) | Almacenamiento seguro e histórico local (máximo 10 elementos) persistido mediante `SharedPreferences`. |
| 📄 [Utils.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/utils/Utils.kt) | Métodos reutilizables para comprobar el estado de la red, mostrar alertas nativas modales y realizar el borrado de datos. |

---

## Requisitos de Entorno de Desarrollo 📱⚙️

- **Lenguaje:** Kotlin 1.9+
- **Compilador Gradle:** Gradle 8.7+
- **SDK Mínimo (minSdkVersion):** Android 8.0 (API 26) - *Requerido para la correcta compatibilidad de WebSettings de seguridad y manejo de cifrados.*
- **SDK de Destino (targetSdkVersion):** Android 14 (API 34)
- **IDE Recomendado:** Android Studio Iguana (2023.2.1) o superior.

---

## Instalación y Configuración del Proyecto 🚀

### 1. Clonar el repositorio
```bash
git clone <URL_DEL_REPOSITORIO>
cd NavegadorAula
```

### 2. Importar el proyecto
- Inicie Android Studio y seleccione **File > Open**.
- Navegue a la carpeta del proyecto `NavegadorAula` y confirme la apertura.
- Permita la sincronización del archivo `build.gradle.kts` e importación de dependencias.

### 3. Configuración de Google Safe Browsing
El servicio está pre-configurado de manera global en el manifiesto principal ([AndroidManifest.xml](file:///c:/laragon/www/NavegadorAula/app/src/main/AndroidManifest.xml)):

```xml
<meta-data
    android:name="android.webkit.WebView.EnableSafeBrowsing"
    android:value="true" />
```

> [!NOTE]
> Para el correcto funcionamiento de Google Safe Browsing, la aplicación móvil y los servicios de Google Play (Google Play Services) del dispositivo deben encontrarse actualizados. Si el dispositivo no cuenta con estos servicios o no tiene conectividad, el navegador continuará operando y defendiendo al usuario a través del resto de las capas locales.

### 4. Compilación y Ejecución
- Habilite el modo de depuración USB en su dispositivo físico o inicie un emulador AVD con Android 8.0 (API 26) o superior.
- En la barra superior de Android Studio presione el botón **Run** (ícono de reproducir) o presione `Shift + F10`.

---

## Personalización de Filtros y Reglas 🔧

### Modificar o Añadir Dominios Bloqueados
Para añadir nuevos portales o redes sociales a la lista negra general de dominios, edite el archivo [BlockedDomains.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/data/BlockedDomains.kt) agregando el host en minúsculas en el conjunto correspondiente:

```kotlin
private val adultDomains: Set<String> = setOf(
    "pornhub.com",
    "nuevodominio.com", // Añadir nuevo aquí
    // ...
)
```

### Configurar Nuevas Palabras Clave y Umbrales
Para alterar los diccionarios de filtrado o cambiar el umbral tolerado antes del bloqueo automático, diríjase a [BlockedKeywords.kt](file:///c:/laragon/www/NavegadorAula/app/src/main/java/com/example/navegadoraula/data/BlockedKeywords.kt). 

Por ejemplo, si desea que un sitio con apuestas sea bloqueado a la primera coincidencia (en lugar de 3):

```kotlin
CategoryThreshold(
    name = "gambling",
    keywords = gamblingKeywords,
    threshold = 1 // Umbral modificado a 1 coincidencia
)
```

> [!WARNING]
> Reducir los umbrales de coincidencia a valores muy bajos (como 1) en diccionarios con palabras comunes puede generar **falsos positivos** en sitios educativos reales. Se recomienda mantener los umbrales predefinidos y realizar pruebas de campo exhaustivas.

---

## Cumplimiento de OWASP Mobile Security y Privacidad 🔒

NavegadorAula ha sido codificado siguiendo políticas de seguridad proactivas:
- **No Logs**: Nunca se almacenan ni registran en consola de desarrollo (Logcat) las páginas web restringidas o consultas introducidas por el alumno.
- **Enlace Seguro Forzado**: No se permite tráfico plano. El archivo de seguridad de red [network_security_config.xml](file:///c:/laragon/www/NavegadorAula/app/src/main/res/xml/network_security_config.xml) deshabilita de forma terminante las peticiones en texto claro a nivel de sistema de red del sistema operativo.
- **Bypass de Popup**: Se inactiva la apertura de pestañas secundarias o ventanas JavaScript (`window.open`), asegurando que la navegación permanezca en la vista y URL controlada bajo la supervisión de los instructores.
