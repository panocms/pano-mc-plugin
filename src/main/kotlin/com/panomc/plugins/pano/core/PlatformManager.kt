package com.panomc.plugins.pano.core

import ch.jamiete.mcping.MinecraftPing
import ch.jamiete.mcping.MinecraftPingOptions
import com.panomc.plugins.pano.core.config.ConfigManager
import com.panomc.plugins.pano.core.helper.PanoPluginMain
import com.panomc.plugins.pano.core.helper.ServerData
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class PlatformManager(
    private val vertx: Vertx,
    private val logger: Logger,
    private val configManager: ConfigManager,
    private val webClient: WebClient,
    private val httpClient: HttpClient,
    private val serverData: ServerData,
    private val pluginMain: PanoPluginMain
) {
    private var webSocket: WebSocket? = null
    private var canConnect = true // to be able to cancel connection task

    val connectPlatformTask: (delay: Boolean) -> Unit by lazy {
        {
            CoroutineScope(vertx.dispatcher()).launch {
                if (it) {
                    delay(TimeUnit.SECONDS.toMillis(3))
                }

                if (!isPlatformConfigured()) {
                    return@launch
                }

                if (canConnect) {
                    establishConnectionToPlatform()
                }
            }
        }
    }

    fun isPlatformConfigured(): Boolean {
        val platformConfig = configManager.getConfig().getJsonObject("platform") ?: return false

        return !platformConfig.getString("token").isNullOrEmpty()
    }

    fun start() {
        logger.info(pluginMain.translateColor("&eChecking is platform connection configured"))

        if (!isPlatformConfigured()) {
            logger.severe(pluginMain.translateColor("&cThis server has not been connected to any Pano Platform!"))
            logger.severe(pluginMain.translateColor("""&6Type: "/pano connect <platform-address> <platform-code>" to connect Pano Platform."""))
            logger.severe(pluginMain.translateColor("""&6For more information please visit: http://panomc.com/platform-connect"""))

            return
        }

        logger.info(pluginMain.translateColor("&6Connecting to platform..."))

        canConnect = true
        connectPlatformTask.invoke(false)
    }

    fun stop() {
        canConnect = false

        if (!isPlatformConfigured()) {
            return
        }

        runBlocking {
            closeConnection()
        }
    }

    fun getWebSocket() = webSocket

    suspend fun connectNewPlatform(platformAddress: String, platformCode: String) {
        val pingOptions = MinecraftPingOptions().setHostname(serverData.hostAddress()).setPort(serverData.port())
        val pingData = MinecraftPing().getPing(pingOptions)

        var port = 8080
        var host = platformAddress

        if (host.contains(":")) {
            val splitHost = host.split(":")

            host = splitHost[0]

            port = splitHost[1].toInt()
        }

        val requestBody = JsonObject()

        requestBody
            .put("platformCode", platformCode)
            .put("serverName", serverData.serverName())
            .put("playerCount", serverData.playerCount())
            .put("maxPlayerCount", serverData.maxPlayerCount())
            .put("serverType", serverData.serverType())
            .put("serverVersion", serverData.serverVersion())
            .put("host", serverData.hostAddress())
            .put("port", serverData.port())

        if (pingData.favicon != null) {
            requestBody
                .put("favicon", pingData.favicon)
        }

        if (pingData.description != null) {
            requestBody
                .put("motd", pingData.description.text)
        }

        val request = webClient
            .post(port, host, "/api/server/connect")
            .sendJsonObject(requestBody)

        val response: HttpResponse<*>

        try {
            response = request.await()
        } catch (exception: Exception) {
            logger.severe(exception.message)

            throw Exception("&cCouldn't connect to Pano Platform. Check your information. Checkout console for more detail.")
        }

        val body = response.bodyAsJsonObject()

        if (body != null && body.getString("result") == "error") {
            val error = body.getString("error")

            throw Exception(getErrorMessageByErrorCode(error))
        }

        val token = body.getString("token")

        savePlatform(host, port, token)
        canConnect = true
    }

    suspend fun disconnectPlatform() {
        canConnect = false
        closeConnection()

        val platformConfig = configManager.getConfig().getJsonObject("platform")
        val host = platformConfig.getString("host")
        val port = platformConfig.getInteger("port")
        val token = platformConfig.getString("token")

        val request = webClient
            .post(port, host, "/api/server/disconnect")
            .putHeader("Authorization", "Bearer $token")
            .send()

        try {
            request.await()
        } catch (exception: Exception) {
            logger.severe(pluginMain.translateColor("&cError: Failed to connect Pano Platform. Reason: ${exception.message}"))

            throw Exception("&cCouldn't connect to Pano Platform. Be sure platform is up and accessible.")
        }

        removePlatform()
    }

    private suspend fun establishConnectionToPlatform() {
        val platformConfig = configManager.getConfig().getJsonObject("platform")
        val host = platformConfig.getString("host")
        val port = platformConfig.getInteger("port")
        val token = platformConfig.getString("token")

        val webSocketConnectOptions = WebSocketConnectOptions()

        webSocketConnectOptions.host = host
        webSocketConnectOptions.port = port
        webSocketConnectOptions.uri = "/api/server/connection"
        webSocketConnectOptions.method = HttpMethod.GET

        webSocketConnectOptions.putHeader("Authorization", "Bearer $token")

        val webSocket: WebSocket

        try {
            webSocket = httpClient.webSocket(webSocketConnectOptions).await()
        } catch (exception: Exception) {
            if (exception is UpgradeRejectedException) {
                val body = exception.body.toJsonObject()

                if (body != null && body.getString("result") == "error") {
                    val error = body.getString("error")

                    logger.severe(pluginMain.translateColor(getErrorMessageByErrorCode(error)))

                    if (error == PlatformErrorCodes.INVALID_TOKEN.toString()) {
                        removePlatform()

                        return
                    }

                    connectPlatformTask.invoke(true)

                    return
                }
            }

            logger.severe(pluginMain.translateColor("&cError: Failed to connect Pano Platform. Reason: ${exception.message}"))

            connectPlatformTask.invoke(true)

            return
        }

        logger.info(pluginMain.translateColor("&2Connected successfully to the platform!"))

        this.webSocket = webSocket

        webSocket.closeHandler {
            onWebSocketClosed()
        }

        webSocket.handler {
            onHandleWebSocket(it)
        }
    }

    private fun onWebSocketClosed() {
        webSocket = null

        logger.info(pluginMain.translateColor("&eDisconnected from the platform."))

        if (canConnect) {
            logger.info(pluginMain.translateColor("&eRetrying to connect..."))

            connectPlatformTask.invoke(true)
        }
    }

    private fun onHandleWebSocket(buffer: Buffer) {
    }

    private suspend fun closeConnection() {
        webSocket?.close()?.await()
    }

    private fun savePlatform(host: String, port: Int, token: String) {
        val platformConfig = configManager.getConfig().getJsonObject("platform")


        platformConfig.put("host", host)
        platformConfig.put("port", port)
        platformConfig.put("token", token)

        configManager.saveConfig()
    }

    private fun removePlatform() {
        val platformConfig = configManager.getConfig().getJsonObject("platform")

        platformConfig.put("host", "")
        platformConfig.put("port", 8080)
        platformConfig.put("token", "")

        configManager.saveConfig()
    }

    private fun getErrorMessageByErrorCode(error: String): String {
        if (error == PlatformErrorCodes.NEED_PERMISSION.toString()) {
            return "&cError: Need permission. &ePlease allow this server in panel of Pano Platform."
        }

        if (error == PlatformErrorCodes.INSTALLATION_REQUIRED.toString()) {
            return "&cError: Platform is not installed. Please first install your Pano platform."
        }

        if (error == PlatformErrorCodes.INVALID_TOKEN.toString()) {
            return "&cError: Token is invalid. Please reconnect Pano platform."
        }

        if (error == PlatformErrorCodes.INVALID_PLATFORM_CODE.toString()) {
            return "&cError: Platform code is invalid, check your information."
        }

        return "&cError: Failed to connect Pano Platform. Reason: $error"
    }
}