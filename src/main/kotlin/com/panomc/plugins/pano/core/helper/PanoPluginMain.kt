package com.panomc.plugins.pano.core.helper

import com.panomc.plugins.pano.core.command.Command
import com.panomc.plugins.pano.core.event.Listener
import java.io.File
import java.net.URLClassLoader
import java.util.logging.Logger

interface PanoPluginMain {
    fun getDataFolder(): File

    fun getLogger(): Logger

    fun registerCommands(commands: List<Command>)

    fun unregisterCommands(commands: List<Command>)

    fun registerSchedule(task: () -> Unit)

    fun stopSchedule(task: () -> Unit)

    fun unregisterSchedules(tasks: List<() -> Unit>)

    fun getServerData(): ServerData

    fun getPluginClassLoader(): URLClassLoader

    fun translateColor(text: String): String

    fun registerEventListeners(listeners: List<Listener>)

    fun unregisterEventListeners(listeners: List<Listener>)
}