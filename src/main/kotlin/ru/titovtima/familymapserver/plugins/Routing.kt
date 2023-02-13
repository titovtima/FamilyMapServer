package ru.titovtima.familymapserver.plugins

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import ru.titovtima.familymapserver.*

fun Application.configureRouting() {
    routing {
        post("/auth/login") {
            val usersList = ServerData.usersList
            val userData = call.receive<UserLogInData>()
            if (usersList.checkUserPassword(userData))
                call.respond(HttpStatusCode.OK, "Successfully logged in")
            else
                call.respond(HttpStatusCode.Forbidden, "Log in error")
        }
        post("/auth/registration") {
            val usersList = ServerData.usersList
            val newUser = call.receive<UserNoIdData>()
            if (usersList.addUser(newUser)) {
                call.respond(HttpStatusCode.Created, "Successfully registered user")
            } else
                call.respond(HttpStatusCode.Forbidden, "Registration error")
        }
        post("/location") {
            val userLocation = call.receive<UserLocationData>()
            if (userLocation.writeToDatabase())
                call.respond(HttpStatusCode.OK, "Successfully wrote location")
            else
                call.respond(HttpStatusCode.BadGateway, "Error posting location")
        }
    }
}
