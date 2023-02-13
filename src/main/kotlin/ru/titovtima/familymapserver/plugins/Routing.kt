package ru.titovtima.familymapserver.plugins

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import ru.titovtima.familymapserver.*

fun Application.configureRouting() {
    routing {
        post("/auth/registration") {
            val usersList = ServerData.usersList
            val newUserData = call.receive<UserNoIdData>()
            val addedUser = usersList.addUser(newUserData)
            if (addedUser != null) {
                call.respond(HttpStatusCode.Created, addedUser.id)
            } else
                call.respond(HttpStatusCode.Forbidden, "Registration error")
        }
        authenticate("auth-basic") {
            post("/auth/login") {
                val userIdString = call.principal<UserIdPrincipal>()?.name
                if (userIdString != null)
                    call.respond(HttpStatusCode.OK, userIdString)
                else
                    call.respond(HttpStatusCode.Forbidden, "Log in error")
            }
            post("/location") {
                val userId = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                val userLocation = call.receive<UserLocationData>()
                if (userId == null || userId != userLocation.userId) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    if (userLocation.writeToDatabase())
                        call.respond(HttpStatusCode.OK, "Successfully wrote location")
                    else
                        call.respond(HttpStatusCode.BadGateway, "Error posting location")
                }
            }
        }
    }
}
