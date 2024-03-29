package ru.titovtima.familymapserver.plugins

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import ru.titovtima.familymapserver.*
import java.util.*

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
        get("/username/{login}") {
            val login = call.parameters["login"] ?: return@get call.respond(HttpStatusCode.BadRequest,
                "Cannot get username: login is not provided")
            val usersList = ServerData.usersList
            val user = usersList.getUser(login) ?: return@get call.respond(HttpStatusCode.BadRequest,
                "User with login $login is not found")
            return@get call.respond(user.getName())
        }
        authenticate("auth-basic") {
            get("/auth/login") {
                val usersList = ServerData.usersList
                val user = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, "Log in error")
                call.respond(HttpStatusCode.OK, user.jsonStringToSend())
            }
            post("/auth/changePassword") {
                val usersList = ServerData.usersList
                val user = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Log in error")
                val newPassword = call.receiveText()
                user.changePassword(newPassword)
                call.respond("Password changed")
            }
            post("/auth/changeName") {
                val usersList = ServerData.usersList
                val user = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Log in error")
                val newName = call.receiveText()
                user.setName(newName)
                call.respond("Name changed")
            }
            post("/location") {
                val usersList = ServerData.usersList
                val user = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Error reading user")
                val location = call.receive<Location>()
                user.updateLastLocation(location)
                call.respond(HttpStatusCode.OK, "Successfully wrote location")
            }
            get("/shareLocationAsks") {
                val usersList = ServerData.usersList
                val userAsking = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, "Error reading user")
                val resultString = userAsking.getSharingLocationAsks().joinToString("\n")
                return@get call.respond(HttpStatusCode.OK, resultString)
            }
            get("/location/{option}/{userLogin}") {
                val userIdAsking = call.principal<UserIdPrincipal>()?.name?.toIntOrNull() ?: return@get call.respond(
                    HttpStatusCode.Unauthorized, "Error reading authorization data")
                val userIdAsked = call.parameters["userLogin"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, "Error reading user login asked for location")
                val usersList = ServerData.usersList
                val userAsked = usersList.getUser(userIdAsked) ?: return@get call.respond(
                    HttpStatusCode.NotFound, "No user with login $userIdAsked")
                if (userAsked.checkSharingLocation(userIdAsking) || userAsked.id == userIdAsking) {
                    when (val option = call.parameters["option"]) {
                        "last" -> {
                            val location = userAsked.getLastLocation() ?: return@get call.respond(
                                HttpStatusCode.NotFound, "User's location in unknown")
                            val locationString = Base64.getEncoder().encodeToString(location.toByteArray())
                            return@get call.respond(HttpStatusCode.OK, locationString)
                        }
                        "history" -> {
                            val locationHistory = userAsked.getLocationHistory()
                            return@get if (locationHistory.isEmpty())
                                call.respond(HttpStatusCode.NotFound, "User's location history in unknown")
                            else {
                                val array = arrayListOf<Byte>()
                                locationHistory.forEach { location -> array.addAll(location.toByteArray().toList()) }
                                val locationString = Base64.getEncoder().encodeToString(array.toByteArray())
                                call.respond(HttpStatusCode.OK, locationString)
                            }
                        }
                        else -> return@get call.respond(HttpStatusCode.BadRequest, "No option $option")
                    }
                } else {
                    return@get call.respond(
                        HttpStatusCode.Forbidden, "You have no permission to read user's location")
                }
            }
            post("/contacts/{action}") {
                val usersList = ServerData.usersList
                val user = call.principal<UserIdPrincipal>()?.name?.toIntOrNull()
                    ?.let { usersList.getUser(it) } ?: return@post call.respond(
                    HttpStatusCode.Unauthorized, "Error reading user")
                val contactReceiveData = call.receive<User.ContactReceiveData>()
                when (val action = call.parameters["action"]) {
                    "add" -> {
                        val contact = user.addContact(contactReceiveData)
                        return@post if (contact != null)
                            call.respond(HttpStatusCode.Created, contact.toJsonString())
                        else
                            call.respond(HttpStatusCode.BadRequest, "Error adding contact")
                    }
                    "update" -> {
                        return@post if (user.updateContact(contactReceiveData))
                            call.respond(HttpStatusCode.OK, "Contact updated")
                        else
                            call.respond(HttpStatusCode.BadRequest, "Contact doesn't exist")
                    }
                    "delete" -> {
                        if (user.deleteContact(contactReceiveData))
                            return@post call.respond(HttpStatusCode.OK, "Contact deleted")
                        else
                            return@post call.respond(HttpStatusCode.BadRequest, "Error deleting contact")
                    }
                    else -> return@post call.respond(HttpStatusCode.BadRequest, "No option $action")
                }
            }
        }
    }
}
