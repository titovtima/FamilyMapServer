package ru.titovtima.familymapserver.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import ru.titovtima.familymapserver.ServerData

fun Application.configureAuthentication() {
    authentication {
        basic(name = "auth-basic") {
            realm = "FamilyMapServer"
            validate { credentials ->
                val usersList = ServerData.usersList
                if (usersList.checkUserPassword(credentials.name, credentials.password)) {
                    UserIdPrincipal(usersList.getUser(credentials.name)?.id.toString())
                } else {
                    null
                }
            }
        }
    }
}