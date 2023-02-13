package ru.titovtima.familymapserver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ru.titovtima.familymapserver.plugins.configureRouting
import ru.titovtima.familymapserver.plugins.configureSerialization
import java.sql.DriverManager
import kotlin.concurrent.thread

fun main() {
    thread {
        while (true) {
            deleteOldLocations()
            Thread.sleep(1000*60*60)
        }
    }

    embeddedServer(Netty, port = 3002, host = "localhost", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureRouting()
    configureSerialization()
}

class ServerData {
    companion object {
        val databaseConnection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/postgres", "postgres", "postgres")
        val usersList = UsersList.readFromDatabase()
    }
}
