package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class UserRegistrationData(val login: String, val password: String, val name: String) {
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
    private val shareLocationToUsers = mutableSetOf<Int>()

    constructor(id: Int, userRegistrationData: UserRegistrationData):
            this(id, userRegistrationData.login, userRegistrationData.password, userRegistrationData.name)

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

    fun shareLocation(userId: Int, writeToDb: Boolean = true) {
        if (shareLocationToUsers.contains(userId)) return
        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "insert into UserShareLocation (userSharingId, userSharedToId) values (?, ?) "
            )
            query.setInt(1, id)
            query.setInt(2, userId)
            query.execute()
        }
        shareLocationToUsers.add(userId)
    }

    fun stopSharingLocation(userId: Int, writeToDb: Boolean = true) {
        if (!shareLocationToUsers.contains(userId)) return
        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "delete from UserShareLocation where userSharingId = ? and userSharedToId = ?"
            )
            query.setInt(1, id)
            query.setInt(2, userId)
            query.execute()
        }
        shareLocationToUsers.remove(userId)
    }

    fun checkSharingLocation(userId: Int) = shareLocationToUsers.contains(userId)
}

class UsersList {
    private val map = mutableMapOf<Int, User>()
    val loginToIdMap = mutableMapOf<String, Int>()
    val connection = ServerData.databaseConnection

    fun getUser(id: Int) = map[id]

    fun getUser(login: String) =
        if (loginToIdMap[login] != null)
            map[loginToIdMap[login]]
        else null

    fun addUser(userRegistrationData: UserRegistrationData) =
        if (loginToIdMap.containsKey(userRegistrationData.login)) null
        else {
            val user = User(userRegistrationData.writeToDatabase(connection), userRegistrationData)
            loginToIdMap[user.login] = user.id
            map[user.id] = user
            user
        }

    fun checkUserPassword(login: String, password: String) =
        getUser(login)?.checkPassword(password) == true

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
            val queryReadUsers = connection.prepareStatement("select * from \"User\"")
            val resultReadUsers = queryReadUsers.executeQuery()
            val usersList = UsersList()
            while(resultReadUsers.next()) {
                val id = resultReadUsers.getInt("id")
                val login = resultReadUsers.getString("login")
                val password = resultReadUsers.getString("password")
                val name = resultReadUsers.getString("name")

                val user = User(id, login, password, name)
                usersList.map[id] = user
                usersList.loginToIdMap[login] = id
            }

            val queryReadLocationSharing = connection.prepareStatement("select * from UserShareLocation")
            val resultReadLocationSharing = queryReadLocationSharing.executeQuery()
            while (resultReadLocationSharing.next()) {
                val userSharingId = resultReadLocationSharing.getInt("userSharingId")
                val userSharedToId = resultReadLocationSharing.getInt("userSharedToId")
                usersList.getUser(userSharingId)?.shareLocation(userSharedToId, false)
            }

            return usersList
        }
    }
}