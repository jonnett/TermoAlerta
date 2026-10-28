# TermoAlerta — Monitoreo de temperatura IoT con alertas

Aplicación Android (Kotlin + Jetpack Compose) para la asignatura **TI3042 Aplicaciones Móviles para IoT — Unidad 2**.

Dos teléfonos Android se conectan entre sí por **WiFi** o **Bluetooth**:

| Rol | Qué hace |
| --- | --- |
| **Sensor** | Mide la temperatura (simulada por ahora; después, un Arduino UNO R4) y la envía cada 1–10 s. |
| **Monitor** | Recibe las lecturas, las muestra en un indicador y un gráfico, las guarda en SQLite y, si superan el **límite configurado**, lanza una **alerta** (notificación con sonido de alarma y vibración). |

El límite se configura en el monitor y se envía automáticamente al sensor, así ambos teléfonos muestran el estado.

---

## 1. Problemática y funcionalidades

**Problema:** en entornos como salas de servidores, cámaras de frío, invernaderos o procesos industriales (OT), una temperatura fuera de rango puede dañar equipos o productos si nadie se entera a tiempo.

**Funcionalidades:**

1. Login con usuario (correo) y contraseña — Firebase Authentication. Registro, inicio de sesión y recuperación de contraseña.
2. Elegir el tipo de conexión: WiFi o Bluetooth.
3. Elegir el rol del teléfono: Sensor o Monitor.
4. Vinculación con un **código de un solo uso**: el monitor muestra un código de 6 dígitos y el sensor solo lo escribe. Por WiFi el sensor encuentra al monitor automáticamente en la red (no hay que escribir la IP). El código vence a los 5 minutos y cifra la conexión.
5. **El usuario elige la temperatura límite**: la escribe (ej. 27,5 °C) o la ajusta con botones −/+ (rango −20 °C a 100 °C). El monitor no empieza hasta que el usuario guarda su límite, y se puede cambiar en cualquier momento durante el monitoreo.
6. Alerta cuando se supera el límite: notificación, sonido de alarma, vibración y aviso visual en pantalla; aviso cuando la temperatura se normaliza.
7. Historial de lecturas en SQLite con resumen (mínimo, máximo, promedio, cantidad de alertas) y filtro "solo alertas".
8. Simulación de temperatura (automática o manual con deslizador) mientras se integra el Arduino.

## 2. Herramientas

| Herramienta | Uso | Justificación |
| --- | --- | --- |
| Android Studio + Kotlin | Desarrollo | Exigido por la guía; Kotlin es el lenguaje recomendado por Google para Android. |
| Jetpack Compose + Material 3 | Interfaz | Estándar actual de diseño de interfaces Android. |
| Firebase Authentication | Login | Ver sección 4. |
| SQLite (`SQLiteOpenHelper`) | Historial de lecturas | Funciona sin internet, viene incluido en Android y permite consultas inmediatas en el teléfono monitor. |
| Sockets TCP (`java.net`) | Comunicación WiFi | Incluido en Android, sin librerías externas. |
| Bluetooth clásico RFCOMM (`android.bluetooth`) | Comunicación Bluetooth | Incluido en Android, flujo de datos estable y simple. |
| `javax.crypto` (AES-GCM, PBKDF2) | Cifrado | Incluido en Android; algoritmos estándar recomendados. |

## 3. Comunicación inalámbrica

| | WiFi (TCP 5050 + búsqueda UDP 5051) | Bluetooth (RFCOMM) |
| --- | --- | --- |
| **Distancia** | Todo el alcance de la red local (decenas de metros, varias salas) | ~10 m |
| **Consumo de energía** | Mayor | Menor |
| **Estabilidad** | Muy estable si la red es buena; permite reconexión | Estable a corta distancia, sensible a obstáculos |
| **Requisitos** | Misma red WiFi (o un teléfono como punto de acceso) | Teléfonos emparejados previamente |
| **Cuándo usarlo** | Monitoreo a distancia; es el que usará el Arduino UNO R4 WiFi | Sin red disponible, equipos cercanos |

Por eso la app ofrece **las dos**: el usuario elige según el lugar.

**Búsqueda automática por WiFi:** el sensor envía `TA1?` por difusión UDP (puerto 5051) a la red local; cada monitor que está esperando responde `TA1! 5050 <nombre>` y así el sensor obtiene su IP. El código **no** viaja en estos mensajes.

**Protocolo de la conexión** (una línea de texto por mensaje):

```
Monitor -> Sensor:  TA1 <sal aleatoria en base64>        (único mensaje en claro)
Sensor  -> Monitor: cifrado({"t":"hello","role":"sensor","name":"..."})
Monitor -> Sensor:  cifrado({"t":"hello","role":"monitor","name":"..."})   o "ERR PIN" si el código no coincide
Sensor  -> Monitor: cifrado({"t":"temp","v":24.6,"ts":...,"src":"simulado"})   cada N segundos
Monitor -> Sensor:  cifrado({"t":"limit","v":30.0})                           al conectar y al cambiar el límite
```

## 4. Autenticación

Se eligió **Firebase Authentication (correo y contraseña)**:

- **Seguridad:** Firebase guarda las contraseñas con hash (scrypt) en sus servidores y toda la comunicación usa HTTPS/TLS. La app nunca guarda la contraseña.
- **Escalabilidad:** las cuentas viven en la nube; el mismo usuario puede entrar desde cualquier teléfono.
- **Facilidad:** no requiere programar ni mantener un servidor propio.

Política de contraseña al registrarse: mínimo 8 caracteres combinando letras y números. Firebase además bloquea temporalmente tras muchos intentos fallidos.

## 5. Seguridad (ISO/IEC 27400 — lineamientos IoT)

| Control | Implementación |
| --- | --- |
| Autenticación de usuarios | Firebase Authentication; sin sesión no se accede a ninguna pantalla. |
| Cifrado de credenciales | Contraseñas gestionadas por Firebase (hash + HTTPS). |
| Conexiones seguras | Tráfico HTTP sin cifrar prohibido (`network_security_config.xml`). |
| Cifrado de datos del dispositivo IoT | Cada lectura viaja cifrada con **AES-256-GCM**; la clave se deriva del código de vinculación con **PBKDF2-HMAC-SHA256** (60.000 iteraciones) y una sal aleatoria por conexión. El código nunca se envía por la red. |
| Integridad | AES-GCM detecta mensajes alterados; se descartan. |
| Autenticación de dispositivos | Código aleatorio de 6 dígitos (generador seguro) de **un solo uso**: vence a los 5 minutos, se cambia después de cada conexión y tras 5 intentos fallidos. Un código incorrecto es rechazado. Pensado para red local; no se expone a internet. |
| Validación de entradas | Temperaturas fuera de −50…150 °C, mensajes desconocidos o demasiado largos se descartan. Consultas SQL con parámetros. |
| Mínimo privilegio | Solo los permisos necesarios, pedidos en tiempo de ejecución (Bluetooth, notificaciones). |
| Protección de datos en el teléfono | Base de datos y ajustes privados de la app; respaldo en la nube y transferencia deshabilitados. |
| Disponibilidad | El monitor detecta si el sensor deja de enviar datos (30 s) y vuelve a esperar conexión. |

## 6. Configurar Firebase (una sola vez)

1. Entra a <https://console.firebase.google.com> y crea un proyecto.
2. **Agregar app → Android**. Nombre del paquete: `com.example.myapplication`. Registra la app.
3. Descarga **google-services.json** y cópialo en la carpeta **`app/`** del proyecto (al lado de `app/build.gradle.kts`).
   No hace falta agregar el plugin de Google Services: `app/build.gradle.kts` ya lee el archivo.
4. En la consola: **Authentication → Comenzar → Método de acceso → Correo electrónico/contraseña → Habilitar**.
5. En Android Studio: **File → Sync Project with Gradle Files** y ejecuta la app.

> Mientras no esté `google-services.json`, la app compila igual. En compilaciones de prueba (debug) aparece el botón "Entrar sin login" para probar la conexión entre teléfonos; desaparece en cuanto Firebase está configurado.

## 7. Cómo probar con dos teléfonos

**Por WiFi**
1. Conecta ambos teléfonos a la misma red WiFi (o activa el punto de acceso en uno y conecta el otro).
2. Teléfono A → **Monitor** → escribe y guarda la temperatura límite → WiFi → **Esperar al sensor**. Aparece un código de 6 dígitos.
3. Teléfono B → **Sensor** → WiFi → escribe el código → **Buscar monitor y conectar**.
   Si la red bloquea la búsqueda automática, toca "¿No lo encuentra? Conectar con la IP" y escribe la IP que aparece bajo el código en el monitor.

**Por Bluetooth**
1. Empareja los teléfonos desde *Ajustes → Bluetooth* de Android.
2. Teléfono A → **Monitor** → escribe y guarda la temperatura límite → Bluetooth → **Esperar al sensor**. Aparece el código.
3. Teléfono B → **Sensor** → Bluetooth → escribe el código → elige el teléfono A de la lista → **Conectar**.

Cada código sirve una sola vez: si se corta la conexión, el monitor muestra uno nuevo.

Para ver una alerta rápido: en el monitor guarda un límite (por ejemplo 28 °C); en el sensor apaga **Modo automático** y sube el deslizador por sobre ese valor.

Si solo tienes un teléfono, el emulador de Android Studio puede hacer de **sensor por WiFi** usando la opción de IP manual (el emulador no recibe la búsqueda automática ni tiene Bluetooth).

## 8. Estructura del código

Todo está en `app/src/main/java/com/example/myapplication/`:

| Archivo | Contenido |
| --- | --- |
| `MainActivity.kt` | Navegación entre pantallas y control de acceso. |
| `TermoApp.kt` | Inicialización de la app. |
| `AuthRepository.kt` | Login, registro y recuperación con Firebase. |
| `ConnectionManager.kt` | Servidor/cliente WiFi (TCP) y Bluetooth (RFCOMM), código de un solo uso, búsqueda automática UDP, saludo seguro. |
| `Protocol.kt` | Mensajes JSON y cifrado AES-GCM (`SecureChannel`). |
| `MonitorController.kt` | Recepción, evaluación del límite, alertas, guardado. |
| `SensorController.kt` / `TemperatureSource.kt` | Lectura (simulada) y envío periódico. |
| `AlertNotifier.kt` | Canales y notificaciones de alerta. |
| `ReadingsDatabase.kt` | Historial en SQLite. |
| `AppSettings.kt` | Límite elegido por el usuario, sonido, intervalo y tipo de conexión. |
| `LimitEditor.kt` | Campo donde el usuario escribe y guarda su temperatura límite. |
| `*Screen.kt`, `UiComponents.kt`, `TermoTheme.kt` | Interfaz en Compose. |

La carpeta `ui/theme/` que generó la plantilla ya no se usa y se puede borrar.

## 9. Próximo paso: Arduino UNO R4 WiFi

El monitor ya funciona como servidor TCP en el puerto 5050, así que el Arduino UNO R4 WiFi podrá reemplazar al teléfono sensor: busca al monitor con el mismo mensaje UDP (o usa su IP), hace el saludo con el código de vinculación y envía `{"t":"temp",...}` cifrado con AES-GCM (por ejemplo con la librería *Crypto* de rweather). En la app solo habría que mostrar el Arduino como un sensor más.
