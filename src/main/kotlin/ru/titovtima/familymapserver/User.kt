package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.io.FileReader
import java.io.FileWriter

@Serializable
data class UserLogInData(val login: String, val password: String)

@Serializable
class User (val login: String, private var password: String, private var name: String) {
    fun checkPassword(password: String) = password == this.password

    fun changePassword(oldPassword: String, newPassword: String) =
        if (checkPassword(oldPassword)) {
            password = newPassword
            true
        } else {
            false
        }

    fun getName() = name
    fun setName(name: String) {
        this.name = name
    }

    fun writeToFile(writer: FileWriter) {
        writer.write(login + "\n" + password + "\n" + name + "\n")
    }
}

class UsersList {
    private val map = mutableMapOf<String, User>()

    fun addUser(user: User) =
        if (map.containsKey(user.login)) false
        else {
            map[user.login] = user
            true
        }

    fun checkUserPassword(userLogInData: UserLogInData) =
        map[userLogInData.login]?.checkPassword(userLogInData.password) == true

    fun getUser(userLogInData: UserLogInData) =
        if (checkUserPassword(userLogInData))
            map[userLogInData.login]
        else
            null

    fun hasLogin(login: String) = map.containsKey(login)

    fun getUserName(login: String) = map[login]?.getName()

    fun deleteUser(userLogInData: UserLogInData) =
        if (getUser(userLogInData) != null) {
            map.remove(userLogInData.login)
            true
        } else {
            !hasLogin(userLogInData.login)
        }

    fun saveToFile(filename: String = fileToSave) {
        FileWriter(filename).use { writer ->
            for (user in map.values) {
                user.writeToFile(writer)
                writer.write("\n")
            }
        }
    }

    companion object {
        var instance = UsersList()
        const val fileToSave = "datafiles/usersList.txt"

        fun readFromFile(filename: String = fileToSave): UsersList {
            val usersList = UsersList()
            val strings = mutableListOf<String>()
            FileReader(filename).use { reader ->
                strings.addAll(reader.readLines())
            }
            var ind = 0
            while (ind < strings.size) {
                val user = User(strings[ind], strings[ind + 1], strings[ind + 2])
                usersList.addUser(user)
                ind += 4
            }
            instance = usersList
            return usersList
        }
    }
}