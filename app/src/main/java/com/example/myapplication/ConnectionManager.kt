package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

enum class Transport(val label: String) { WIFI("WiFi"), BLUETOOTH("Bluetooth") }

enum class Role { NONE, MONITOR, SENSOR }

sealed interface ConnState {
    data object Idle : ConnState
    data class Waiting(val detail: String) : ConnState
    data class Connecting(val detail: String) : ConnState
    data class Connected(val peerName: String, val transport: Transport) : ConnState
    data class Failed(val message: String) : ConnState
}

/** Teléfono emparejado por Bluetooth que se puede elegir como monitor. */
data class PairedDevice(val name: String, val address: String)

/** Código de vinculación de un solo uso que muestra el monitor. */
data class PairingCode(val code: String, val expiresAt: Long) {
    /** "482913" -> "482 913" para leerlo fácil. */
    val display: String get() = code.substring(0, 3) + " " + code.substring(3)
}

fun isCodeValid(code: String) = code.length == 6 && code.all { it.isDigit() }

/**
 * Maneja la conexión entre los dos teléfonos, por WiFi (TCP) o por Bluetooth (RFCOMM).
 *
 * VINCULACIÓN CON CÓDIGO DE UN SOLO USO
 * - El MONITOR genera un código aleatorio de 6 dígitos y lo muestra en pantalla.
 *   El código vence a los 5 minutos, se cambia después de cada conexión y también
 *   tras 5 intentos con un código incorrecto.
 * - El SENSOR escribe ese código. Por WiFi busca solo al monitor en la red local
 *   (mensaje UDP de difusión al puerto 5051), sin que el usuario escriba la IP.
 * - El código nunca viaja por la red: con él cada lado deriva la clave AES que cifra
 *   la conexión. Si el código no coincide, el saludo falla y el monitor rechaza al sensor.
 *
 * Saludo seguro (TCP puerto 5050 o Bluetooth):
 *   monitor -> "TA1 <sal en base64>"            (en claro)
 *   sensor  -> cifrado(Hello sensor)
 *   monitor -> cifrado(Hello monitor)  o  "ERR PIN" si el código no coincide
 * Desde ahí todos los mensajes van cifrados, uno por línea.
 */
object ConnectionManager {

    const val WIFI_PORT = 5050
    const val DISCOVERY_PORT = 5051
    private val BT_UUID: UUID = UUID.fromString("8f3c2a10-6b1e-4d7a-9c55-2b7e1f0a9d41")
    private const val BT_SERVICE_NAME = "TermoAlerta"
    private const val GREETING = "TA1 "
    private const val PIN_ERROR = "ERR PIN"
    private const val DISCOVER_MESSAGE = "TA1?"
    private const val ANNOUNCE_PREFIX = "TA1! "
    private const val CODE_LIFETIME_MS = 5 * 60_000L
    private const val MAX_WRONG_ATTEMPTS = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val random = SecureRandom()

    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Message> = _incoming.asSharedFlow()

    private val _pairingCode = MutableStateFlow<PairingCode?>(null)
    /** Código que el monitor está mostrando ahora (null si no está esperando). */
    val pairingCode: StateFlow<PairingCode?> = _pairingCode.asStateFlow()

    @Volatile var role: Role = Role.NONE
        private set

    /** Nombre que se muestra al otro teléfono. */
    @Volatile var localName: String = Build.MODEL ?: "Android"

    private class Session(val link: LineLink, val channel: SecureChannel)
    private class Handshake(val link: LineLink, val channel: SecureChannel, val peerName: String)
    private class AcceptTimeout : Exception()
    private class WrongCodeException :
        IOException("Código incorrecto o vencido. Revisa el código que muestra el monitor.")

    private enum class SessionEnd { WRONG_CODE, USED, ABORTED }

    @Volatile private var session: Session? = null
    @Volatile private var serverToClose: Closeable? = null
    @Volatile private var discoverySocket: DatagramSocket? = null
    @Volatile private var generation = 0
    private var job: Job? = null
    private var discoveryJob: Job? = null

    // ------------------------------------------------------------------ MONITOR (servidor)

    fun startMonitor(context: Context, transport: Transport) {
        stop()
        role = Role.MONITOR
        val app = context.applicationContext
        val gen = generation
        if (transport == Transport.WIFI) startDiscoveryResponder(app, gen)

        job = scope.launch {
            var notice = ""
            var code = newPairingCode()
            var wrongAttempts = 0
            try {
                while (isActive && gen == generation) {
                    if (System.currentTimeMillis() >= code.expiresAt) {
                        code = newPairingCode()
                        wrongAttempts = 0
                        notice = "El código anterior venció. "
                    }
                    if (gen == generation) _pairingCode.value = code
                    val remaining = (code.expiresAt - System.currentTimeMillis()).coerceAtLeast(1000L)

                    val raw = try {
                        when (transport) {
                            Transport.WIFI -> acceptWifi(gen, notice, remaining)
                            Transport.BLUETOOTH -> acceptBluetooth(app, gen, notice, remaining)
                        }
                    } catch (e: AcceptTimeout) {
                        continue // el código venció sin que nadie se conectara: se genera otro
                    }

                    val (end, message) = runMonitorSession(gen, raw, code.code, transport)
                    notice = message
                    when (end) {
                        SessionEnd.WRONG_CODE -> if (++wrongAttempts >= MAX_WRONG_ATTEMPTS) {
                            code = newPairingCode()
                            wrongAttempts = 0
                            notice += "Por seguridad se generó un código nuevo. "
                        }
                        SessionEnd.USED -> {
                            code = newPairingCode() // un solo uso
                            wrongAttempts = 0
                        }
                        SessionEnd.ABORTED -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(gen, ConnState.Failed(friendlyError(e)))
            } finally {
                closeServer()
                if (gen == generation) _pairingCode.value = null
            }
        }
    }

    private fun newPairingCode() = PairingCode(
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)),
        expiresAt = System.currentTimeMillis() + CODE_LIFETIME_MS,
    )

    private fun acceptWifi(gen: Int, notice: String, timeoutMs: Long): RawLink {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(WIFI_PORT))
        server.soTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        serverToClose = server
        setState(gen, ConnState.Waiting(notice + "Esperando al sensor en la red WiFi…"))
        try {
            val socket = try {
                server.accept()
            } catch (e: SocketTimeoutException) {
                throw AcceptTimeout()
            }
            socket.tcpNoDelay = true
            // El sensor envía al menos cada 10 s; si pasan 30 s sin datos, se da por perdida la conexión
            socket.soTimeout = 30_000
            return RawLink(socket.getInputStream(), socket.getOutputStream(), socket,
                socket.inetAddress?.hostAddress ?: "WiFi")
        } finally {
            closeServer()
        }
    }

    @SuppressLint("MissingPermission")
    private fun acceptBluetooth(context: Context, gen: Int, notice: String, timeoutMs: Long): RawLink {
        val adapter = bluetoothAdapter(context) ?: throw IOException("Este teléfono no tiene Bluetooth")
        if (!hasBluetoothPermission(context)) throw IOException("Falta el permiso de Bluetooth")
        if (!adapter.isEnabled) throw IOException("Activa el Bluetooth del teléfono")
        val server = adapter.listenUsingRfcommWithServiceRecord(BT_SERVICE_NAME, BT_UUID)
        serverToClose = server
        setState(gen, ConnState.Waiting(notice + "Esperando al sensor por Bluetooth en \"${adapter.name}\"…"))
        val started = System.currentTimeMillis()
        try {
            val socket = try {
                server.accept(timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            } catch (e: IOException) {
                // accept(timeout) avisa del tiempo agotado con una IOException
                if (gen == generation && System.currentTimeMillis() - started >= timeoutMs - 500) throw AcceptTimeout()
                throw e
            }
            val peer = socket.remoteDevice?.name ?: socket.remoteDevice?.address ?: "Bluetooth"
            return RawLink(socket.inputStream, socket.outputStream, socket, peer)
        } finally {
            closeServer()
        }
    }

    private suspend fun runMonitorSession(
        gen: Int, raw: RawLink, code: String, transport: Transport,
    ): Pair<SessionEnd, String> {
        val link = LineLink(raw)
        var connected = false
        try {
            val salt = SecureChannel.newSalt()
            link.writeLine(GREETING + SecureChannel.encodeSalt(salt))
            val channel = SecureChannel.fromPin(code, salt)

            val first = link.readLine() ?: return (SessionEnd.ABORTED to "El sensor se desconectó. ")
            val hello = runCatching { Message.parse(channel.decrypt(first)) }.getOrNull() as? Message.Hello
            if (hello == null) {
                runCatching { link.writeLine(PIN_ERROR) }
                return (SessionEnd.WRONG_CODE to "Un sensor intentó conectarse con un código incorrecto. ")
            }
            link.writeLine(channel.encrypt(Message.Hello("monitor", localName).toJson()))

            if (gen != generation) return (SessionEnd.ABORTED to "")
            connected = true
            _pairingCode.value = null // el código ya se usó
            session = Session(link, channel)
            setState(gen, ConnState.Connected(hello.name, transport))
            _incoming.emit(hello)
            readLoop(link, channel)
            return (SessionEnd.USED to "Se perdió la conexión con el sensor. ")
        } catch (e: IOException) {
            return if (connected) (SessionEnd.USED to "Se perdió la conexión con el sensor. ")
            else (SessionEnd.ABORTED to "El sensor se desconectó. ")
        } finally {
            if (gen == generation) session = null
            link.close()
        }
    }

    /** Responde a los sensores que buscan un monitor en la red WiFi. */
    private fun startDiscoveryResponder(context: Context, gen: Int) {
        discoveryJob = scope.launch {
            val wifi = context.getSystemService(WifiManager::class.java)
            val lock = wifi?.createMulticastLock("termoalerta")?.apply { setReferenceCounted(false) }
            runCatching { lock?.acquire() }
            val socket = try {
                DatagramSocket(null as java.net.SocketAddress?).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            } catch (e: Exception) {
                runCatching { lock?.release() }
                return@launch // sin búsqueda automática; el sensor aún puede usar la IP manual
            }
            discoverySocket = socket
            try {
                val buffer = ByteArray(256)
                while (isActive && gen == generation) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                    if (text == DISCOVER_MESSAGE && _state.value is ConnState.Waiting) {
                        val reply = (ANNOUNCE_PREFIX + "$WIFI_PORT " + localName.take(40)).toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    }
                }
            } catch (e: Exception) {
                // socket cerrado al detener el monitor
            } finally {
                socket.close()
                runCatching { lock?.release() }
            }
        }
    }

    // ------------------------------------------------------------------ SENSOR (cliente)

    /**
     * [target]: por WiFi, null para buscar al monitor automáticamente o una IP escrita a mano;
     * por Bluetooth, la dirección del teléfono emparejado. [code] es el código del monitor.
     */
    fun connectSensor(context: Context, transport: Transport, target: String?, code: String) {
        stop()
        role = Role.SENSOR
        val app = context.applicationContext
        val gen = generation
        job = scope.launch {
            var link: LineLink? = null
            try {
                val handshake: Handshake = when (transport) {
                    Transport.WIFI -> {
                        val candidates = if (target.isNullOrBlank()) {
                            setState(gen, ConnState.Connecting("Buscando el monitor en la red WiFi…"))
                            discoverMonitors().ifEmpty {
                                throw IOException(
                                    "No se encontró ningún monitor esperando en esta red. Revisa que ambos " +
                                        "teléfonos estén en la misma red WiFi y que el monitor muestre su código."
                                )
                            }
                        } else {
                            listOf(target.trim())
                        }
                        var result: Handshake? = null
                        var lastError: Exception? = null
                        for (candidate in candidates) {
                            if (gen != generation) return@launch
                            setState(gen, ConnState.Connecting("Conectando con el monitor…"))
                            try {
                                result = clientHandshake(connectWifi(candidate), code)
                                break
                            } catch (e: IOException) {
                                lastError = e // si hay varios monitores, se prueba el siguiente
                            }
                        }
                        result ?: throw (lastError ?: IOException("No se pudo conectar con el monitor"))
                    }
                    Transport.BLUETOOTH -> {
                        setState(gen, ConnState.Connecting("Conectando por Bluetooth…"))
                        clientHandshake(connectBluetooth(app, target.orEmpty()), code)
                    }
                }
                link = handshake.link
                if (gen != generation) return@launch

                session = Session(handshake.link, handshake.channel)
                setState(gen, ConnState.Connected(handshake.peerName, transport))
                readLoop(handshake.link, handshake.channel)
                setState(gen, ConnState.Failed("Se perdió la conexión con el monitor. Pide el nuevo código en el monitor."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState(gen, ConnState.Failed(friendlyError(e)))
            } finally {
                if (gen == generation) session = null
                link?.close()
            }
        }
    }

    /** Saludo seguro del lado del sensor. Cierra la conexión si falla. */
    private fun clientHandshake(raw: RawLink, code: String): Handshake {
        val link = LineLink(raw)
        try {
            val greeting = link.readLine() ?: throw IOException("El monitor cerró la conexión")
            if (!greeting.startsWith(GREETING)) throw IOException("El otro equipo no es un monitor TermoAlerta")
            val channel = SecureChannel.fromPin(code, SecureChannel.decodeSalt(greeting.removePrefix(GREETING)))
            link.writeLine(channel.encrypt(Message.Hello("sensor", localName).toJson()))

            val reply = link.readLine() ?: throw IOException("El monitor cerró la conexión")
            if (reply == PIN_ERROR) throw WrongCodeException()
            val hello = runCatching { Message.parse(channel.decrypt(reply)) }.getOrNull() as? Message.Hello
                ?: throw IOException("Respuesta inválida del monitor")
            return Handshake(link, channel, hello.name)
        } catch (e: Exception) {
            link.close()
            throw e
        }
    }

    /** Envía "TA1?" por difusión UDP y junta las respuestas de los monitores ("TA1! <puerto> <nombre>"). */
    private fun discoverMonitors(): List<String> {
        val found = LinkedHashSet<String>()
        val ownIps = localIpAddresses().toSet()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val message = DISCOVER_MESSAGE.toByteArray(Charsets.UTF_8)
            val targets = broadcastAddresses()
            val started = System.currentTimeMillis()
            var nextSend = 0L
            val buffer = ByteArray(256)
            while (System.currentTimeMillis() - started < 3500) {
                val now = System.currentTimeMillis()
                if (now >= nextSend) {
                    targets.forEach { address ->
                        runCatching { socket.send(DatagramPacket(message, message.size, address, DISCOVERY_PORT)) }
                    }
                    nextSend = now + 1000
                }
                // Si ya respondió alguien, se espera un poco más por si hay otros y se termina
                if (found.isNotEmpty() && now - started > 1500) break
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                if (!text.startsWith(ANNOUNCE_PREFIX)) continue
                val port = text.removePrefix(ANNOUNCE_PREFIX).substringBefore(' ').trim().toIntOrNull() ?: continue
                val ip = packet.address?.hostAddress ?: continue
                if (ip !in ownIps) found += "$ip:$port"
            }
        }
        return found.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(list) { it.broadcast }
        } catch (e: Exception) {
            // se usa solo la difusión general
        }
        list += InetAddress.getByName("255.255.255.255")
        return list.distinct()
    }

    private fun connectWifi(target: String): RawLink {
        val host = target.substringBefore(':').trim()
        val port = target.substringAfter(':', "").toIntOrNull() ?: WIFI_PORT
        if (host.isEmpty()) throw IOException("Escribe la IP del monitor")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 6000)
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw IOException("No se pudo conectar a $host. ¿El monitor está esperando y en la misma red WiFi?")
        }
        socket.tcpNoDelay = true
        return RawLink(socket.getInputStream(), socket.getOutputStream(), socket, host)
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetooth(context: Context, address: String): RawLink {
        val adapter = bluetoothAdapter(context) ?: throw IOException("Este teléfono no tiene Bluetooth")
        if (!hasBluetoothPermission(context)) throw IOException("Falta el permiso de Bluetooth")
        if (!adapter.isEnabled) throw IOException("Activa el Bluetooth del teléfono")
        if (!BluetoothAdapter.checkBluetoothAddress(address)) throw IOException("Elige el teléfono monitor de la lista")
        val device = adapter.getRemoteDevice(address)
        runCatching { adapter.cancelDiscovery() } // requiere otro permiso en Android 12+, se ignora si falla
        val socket = device.createRfcommSocketToServiceRecord(BT_UUID)
        try {
            socket.connect()
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw IOException("No se pudo conectar por Bluetooth. ¿El monitor está mostrando su código?")
        }
        return RawLink(socket.inputStream, socket.outputStream, socket, device.name ?: address)
    }

    // ------------------------------------------------------------------ común

    private suspend fun readLoop(link: LineLink, channel: SecureChannel) {
        while (true) {
            val line = link.readLine() ?: break
            val message = runCatching { Message.parse(channel.decrypt(line)) }.getOrNull() ?: continue
            _incoming.emit(message)
        }
    }

    /** Envía un mensaje cifrado al otro teléfono. No hace nada si no hay conexión. */
    fun send(message: Message) {
        val current = session ?: return
        scope.launch(writer) {
            try {
                current.link.writeLine(current.channel.encrypt(message.toJson()))
            } catch (e: IOException) {
                current.link.close() // el hilo lector detectará el cierre
            }
        }
    }

    val isConnected: Boolean get() = session != null

    /** Corta cualquier conexión o espera en curso. */
    fun stop() {
        generation++
        job?.cancel()
        job = null
        discoveryJob?.cancel()
        discoveryJob = null
        runCatching { discoverySocket?.close() }
        discoverySocket = null
        closeServer()
        session?.link?.close()
        session = null
        role = Role.NONE
        _pairingCode.value = null
        _state.value = ConnState.Idle
    }

    private fun closeServer() {
        runCatching { serverToClose?.close() }
        serverToClose = null
    }

    private fun setState(gen: Int, newState: ConnState) {
        if (gen == generation) _state.value = newState
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is SecurityException -> "Falta un permiso para usar la conexión"
        is java.net.BindException -> "El puerto $WIFI_PORT está ocupado. Cierra y vuelve a abrir la app"
        else -> e.message ?: "Error de conexión"
    }

    // ------------------------------------------------------------------ utilidades

    fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PairedDevice> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = bluetoothAdapter(context) ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name ?: it.address, it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** IPs IPv4 locales (WiFi o punto de acceso). */
    fun localIpAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    } catch (e: Exception) {
        emptyList()
    }
}

/** Flujos de bytes de un socket TCP o Bluetooth, más cómo cerrarlo. */
class RawLink(val input: InputStream, val output: OutputStream, val closeable: Closeable, val peer: String)

/** Lectura y escritura de una línea de texto por mensaje. */
class LineLink(raw: RawLink) : Closeable {
    private val reader: BufferedReader = raw.input.bufferedReader(Charsets.UTF_8)
    private val writer: BufferedWriter = raw.output.bufferedWriter(Charsets.UTF_8)
    private val closeable = raw.closeable

    fun readLine(): String? {
        val line = reader.readLine() ?: return null
        if (line.length > MAX_LINE) throw IOException("Mensaje demasiado largo")
        return line
    }

    @Synchronized
    fun writeLine(line: String) {
        writer.write(line)
        writer.write("\n")
        writer.flush()
    }

    override fun close() {
        runCatching { closeable.close() }
    }

    private companion object {
        const val MAX_LINE = 4096
    }
}
