package com.panomc.plugins.pano.core.command

import com.panomc.plugins.pano.core.helper.CommandHelper

interface Command {
    val name: String
    val permission: String

    suspend fun handler(commandSender: Any, args: Array<out String>, commandHelper: CommandHelper): Boolean
}