package com.panomc.plugins.pano.spigot

import com.panomc.plugins.pano.core.Pano
import com.panomc.plugins.pano.core.command.Command
import com.panomc.plugins.pano.core.helper.PanoPluginMain
import com.panomc.plugins.pano.core.helper.ServerData
import org.bukkit.Bukkit
import org.bukkit.command.CommandMap
import org.bukkit.plugin.java.JavaPlugin

class SpigotMain : JavaPlugin(), PanoPluginMain {
    private lateinit var pano: Pano
    private val commands = mutableListOf<SpigotCommand>()
    private val scheduledTasks = mutableMapOf<() -> Unit, Int>()
    private val serverData by lazy { SpigotServerData(this) }

    override fun onEnable() {
        pano = Pano.init(this)

        server.scheduler.scheduleSyncDelayedTask(this) {
            if (::pano.isInitialized) {
                pano.onServerStart()
            }
        }
    }

    override fun onDisable() {
        if (::pano.isInitialized) {
            pano.disable()
        }
    }

    override fun registerCommands(commands: List<Command>) {
        val commandMap = getCommandMap()

        commands
            .map { SpigotCommand(it) }
            .forEach { command ->
                this.commands.add(command)

                commandMap.register(name, command)
            }
    }

    override fun unregisterCommands(commands: List<Command>) {
        val commandMap = getCommandMap()

        this.commands
            .forEach { command ->
                command.unregister(commandMap)
            }

        this.commands.clear()
    }

    private fun getCommandMap(): CommandMap {
        val commandMapField = Bukkit.getServer().javaClass.getDeclaredField("commandMap")

        commandMapField.isAccessible = true

        return commandMapField.get(Bukkit.getServer()) as CommandMap
    }

    override fun registerSchedule(task: () -> Unit) {
        if (scheduledTasks.containsKey(task)) {
            stopSchedule(task)
        }

        scheduledTasks[task] = server.scheduler.scheduleSyncRepeatingTask(this, task, 1, 20)
    }

    override fun stopSchedule(task: () -> Unit) {
        scheduledTasks[task]?.let { server.scheduler.cancelTask(it) }
        scheduledTasks.remove(task)
    }

    override fun unregisterSchedules(tasks: List<() -> Unit>) {
        tasks.forEach { task ->
            stopSchedule(task)
        }
    }

    override fun getServerData(): ServerData = serverData

    override fun getPluginClassLoader(): ClassLoader = classLoader
}