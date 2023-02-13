package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class UserLogInData(val login: String, val password: String)

@Serializable
data class UserNoIdData(val login: String, val password: String, val name: String) {
    fun writeToDatabase(connection: Connection = ServerData.databaseConnection): Int {
        val writeQuery = connection.prepareStatement(
            "insert into \"User\" (login, password, name) values (?, ?, ?);")
        writeQuery.setString(1, login)
        writeQuery.setString(2, password)
        writeQuery.setString(3, name)
        writeQuery.execute()

        val getResultQuery = connection.prepareStatement("select id from \"User\" where login = ?")
        getResultQuery.setString(1, login)
        val result = getResultQuery.executeQuery()
        result.next()
        return result.getInt("id")
    }
}

@Serializable
class User (val id: Int, val login: String, private var password: String, private var name: String) {
    constructor(id: Int, userNoIdData: UserNoIdData):
            this(id, userNoIdData.login, userNoIdData.password, userNoIdData.name)

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
}

class UsersList {
    private val map = mutableMapOf<Int, User>()
    private val loginToIdMap = mutableMapOf<String, Int>()
    val connection = ServerData.databaseConnection

    fun getUser(id: Int) = map[id]

    fun getUser(login: String) =
        if (loginToIdMap[login] != null)
            map[loginToIdMap[login]]
        else null

    fun addUser(userNoIdData: UserNoIdData) =
        if (loginToIdMap.containsKey(userNoIdData.login)) false
        else {
            val user = User(userNoIdData.writeToDatabase(connection), userNoIdData)
            loginToIdMap[user.login] = user.id
            map[user.id] = user
            true
        }

    fun checkUserPassword(userLogInData: UserLogInData) =
        getUser(userLogInData.login)?.checkPassword(userLogInData.password) == true

    fun hasLogin(login: String) = loginToIdMap.containsKey(login)

    fun deleteUser(id: Int): User? {
        val user = getUser(id)
        if (user != null) {
            val query = connection.prepareStatement("delete from \"User\" where id = ?")
            query.setInt(1, id)
            query.execute()
            map.remove(id)
            loginToIdMap.remove(user.login)
        }
        return user
    }

    companion object {
        fun readFromDatabase(connection: Connection = ServerData.databaseConnection): UsersList {
            val query = connection.prepareStatement("select * from \"User\"")

            val result = query.executeQuery()

            val usersList = UsersList()
            while(result.next()) {
                val id = result.getInt("id")
                val login = result.getString("login")
                val password = result.getString("password")
                val name = result.getString("name")

                val user = User(id, login, password, name)
                usersList.map[id] = user
                usersList.loginToIdMap[login] = id
            }

            return usersList
        }
    }
}