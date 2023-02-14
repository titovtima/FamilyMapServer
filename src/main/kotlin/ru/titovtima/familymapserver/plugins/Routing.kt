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
            val newUserData = call.receive<UserRegistrationData>()
            val addedUser = usersList.addUser(newUserData)
            if (addedUser != null) {
                call.respond(HttpStatusCode.Created, addedUser.id)
            } else
                call.respond(HttpStatusCode.BadRequest, "Registration error")
        }
        authenticate("auth-basic") {
            post("/auth/login") {
                val userIdString = call.principal<UserIdPrincipal>()?.name
                if (userIdString != null)
                    call.respond(HttpStatusCode.OK, userIdString)
                else
                    call.respond(HttpStatusCode.Unauthorized, "Log in error")
            }
            post("/location") {
                val userId = call.principal<UserIdPrincipal>()?.name?.toIntOrNull() ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Error reading user")
                val location = call.receive<Location>()
                val usersList = ServerData.usersList
                val user = usersList.getUser(userId)
                user?.updateLastLocation(location) ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Error reading user")
                call.respond(HttpStatusCode.OK, "Successfully wrote location")
            }
            post("/shareLocation/share") {
                val usersList = ServerData.usersList
                val userIdSharing = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                val userSharing = userIdSharing?.let { usersList.getUser(it) }
                val userLoginSharedTo = call.receiveText()
                val userIdSharedTo = usersList.loginToIdMap[userLoginSharedTo]
                if (userSharing == null || userIdSharedTo == null || userIdSharedTo == userIdSharing) {
                    call.respond(HttpStatusCode.BadRequest, "Error sharing location")
                } else {
                    userSharing.shareLocation(userIdSharedTo)
                    call.respond(HttpStatusCode.OK, "Successfully shared location")
                }
            }
            post("/shareLocation/stop") {
                val usersList = ServerData.usersList
                val userIdSharing = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                val userSharing = userIdSharing?.let { usersList.getUser(it) }
                val userLoginSharedTo = call.receiveText()
                val userIdSharedTo = usersList.loginToIdMap[userLoginSharedTo]
                if (userSharing == null || userIdSharedTo == null || userIdSharedTo == userIdSharing) {
                    call.respond(HttpStatusCode.BadRequest, "Error stop sharing location")
                } else {
                    userSharing.stopSharingLocation(userIdSharedTo)
                    call.respond(HttpStatusCode.OK, "Successfully stop sharing location")
                }
            }
        }
    }
}
