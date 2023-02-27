package ru.titovtima.familymapserver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ru.titovtima.familymapserver.plugins.configureAuthentication
import ru.titovtima.familymapserver.plugins.configureRouting
import ru.titovtima.familymapserver.plugins.configureSerialization
import java.sql.DriverManager
import kotlin.concurrent.thread

fun main() {
    var deleteOldLocationsFlag = true
    thread {
        while (true) {
            while (!deleteOldLocationsFlag)
                Thread.sleep(500)
            deleteOldLocationsFlag = false
            deleteOldLocations()
        }
    }
    var writeUsersLocationsToDatabaseFlag = false
    thread {
        while (true) {
            while (!writeUsersLocationsToDatabaseFlag)
                Thread.sleep(500)
            writeUsersLocationsToDatabaseFlag = false
            for (user in ServerData.usersList.getList()) {
                val userLocation = user.getLastLocation() ?: continue
                try {
                    writeLocationToDatabase(user.id, userLocation, ServerData.databaseConnection)
                } catch (_: Exception) {}
            }
        }
    }
    thread {
        var counter = 0
        while (true) {
            Thread.sleep(1000*60*5)
            writeUsersLocationsToDatabaseFlag = true
            if (counter == 20) {
                counter = 0
                deleteOldLocationsFlag = true
            }
            counter++
        }
    }

    embeddedServer(Netty, port = 3002, host = "localhost", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureAuthentication()
    configureSerialization()
    configureRouting()
}

class ServerData {
    companion object {
        val databaseConnection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/familymapserver", "postgres", "postgres")
        val usersList = UsersList.readFromDatabase()
    }
}
