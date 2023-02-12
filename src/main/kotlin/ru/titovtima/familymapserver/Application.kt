package ru.titovtima.familymapserver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ru.titovtima.familymapserver.plugins.configureRouting
import ru.titovtima.familymapserver.plugins.configureSerialization

fun main() {
    embeddedServer(Netty, port = 3002, host = "localhost", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureRouting()
    configureSerialization()
}

class ServerData {
    companion object {
        val usersList = UsersList.readFromFile()
    }
}
