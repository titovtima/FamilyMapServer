package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection

@Serializable
data class UserRegistrationData(val login: String, val password: String, val name: String) {
    fun writeToDatabase(connection: Connection = ServerData.databaseConnection): Int {
        val encodedPassword = RSAEncoder.encode(password).toString()
        val writeQuery = connection.prepareStatement(
            "insert into \"User\" (login, password, name) values (?, ?, ?);")
        writeQuery.setString(1, login)
        writeQuery.setString(2, encodedPassword)
        writeQuery.setString(3, name)
        writeQuery.execute()

        val getResultQuery = connection.prepareStatement("select id from \"User\" where login = ?;")
        getResultQuery.setString(1, login)
        val result = getResultQuery.executeQuery()
        result.next()
        return result.getInt("id")
    }
}

class User (val id: Int, val login: String, private var password: String, private var name: String) {
    private val shareLocationToUsers = mutableSetOf<Int>()
    private var lastLocation: Location?
    private val contactsMutable = mutableListOf<Contact>()
    val contacts
    get() = contactsMutable.toList()

    init {
        val connection = ServerData.databaseConnection
        val queryGetLastLocation = connection.prepareStatement(
            "select latitude, longitude, date from UserLocation where userId = ? and date = (" +
                    "select max(date) from UserLocation where userId = ? group by userId);")
        queryGetLastLocation.setInt(1, id)
        queryGetLastLocation.setInt(2, id)
        val resultLastLocation = queryGetLastLocation.executeQuery()
        lastLocation = if (resultLastLocation.next()) {
            val latitude = resultLastLocation.getInt("latitude")
            val longitude = resultLastLocation.getInt("longitude")
            val date = resultLastLocation.getLong("date")
            Location(latitude, longitude, date)
        } else {
            null
        }
    }

    constructor(id: Int, userRegistrationData: UserRegistrationData):
            this(id, userRegistrationData.login,
                RSAEncoder.encode(userRegistrationData.password).toString(),
                userRegistrationData.name)

    fun checkPassword(password: String) = RSAEncoder.encode(password).toString() == this.password

    fun changePassword(newPassword: String) {
        val encodedPassword = RSAEncoder.encode(newPassword).toString()
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement("update \"User\" set password = ? where id = ?;")
        query.setString(1, encodedPassword)
        query.setInt(2, id)
        query.execute()
        password = encodedPassword
    }

    fun getName() = name
    fun setName(name: String) {
        this.name = name
    }

    fun shareLocation(userId: Int, writeToDb: Boolean = true) {
        if (shareLocationToUsers.contains(userId)) return
        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val queryShare = connection.prepareStatement(
                "insert into UserShareLocation (userSharingId, userSharedToId) values (?, ?);"
            )
            queryShare.setInt(1, id)
            queryShare.setInt(2, userId)
            queryShare.execute()

            val queryDeleteAsk = connection.prepareStatement(
                "delete from UserAskForShareLocation where userAskedForId = ? and userAskingId = ?;")
            queryDeleteAsk.setInt(1, id)
            queryDeleteAsk.setInt(2, userId)
            queryDeleteAsk.execute()
        }
        shareLocationToUsers.add(userId)
    }

    fun stopSharingLocation(userId: Int, writeToDb: Boolean = true) {
        if (!shareLocationToUsers.contains(userId)) return
        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "delete from UserShareLocation where userSharingId = ? and userSharedToId = ?;"
            )
            query.setInt(1, id)
            query.setInt(2, userId)
            query.execute()
        }
        shareLocationToUsers.remove(userId)
    }

    fun askForSharingLocation(userId: Int) {
        try {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "insert into UserAskForShareLocation (useraskingid, useraskedforid) values (?, ?);"
            )
            query.setInt(1, id)
            query.setInt(2, userId)
            query.execute()
        } catch (_: Exception) {}
    }

    fun getSharingLocationAsks(): List<String> {
        val resultList = mutableListOf<String>()
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "select userAskingId from UserAskForShareLocation where userAskedForId = ?;")
        query.setInt(1, id)
        val queryResult = query.executeQuery()
        while (queryResult.next()) {
            ServerData.usersList.getUser(queryResult.getInt("userAskingId"))?.login?.let { resultList.add(it) }
        }
        return resultList.toList()
    }

    fun checkSharingLocation(userId: Int) = shareLocationToUsers.contains(userId)

    fun getLastLocation() = lastLocation

    fun updateLastLocation(location: Location) {
        val _lastLocation = lastLocation
        if (_lastLocation == null || _lastLocation.date < location.date) {
            lastLocation = location
        }
    }

    fun getLocationHistory(): List<Location> {
        val resultList = mutableListOf<Location>()
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "select latitude, longitude, date from UserLocation where userId = ? order by date")
        query.setInt(1, id)
        val result = query.executeQuery()
        while (result.next()) {
            val latitude = result.getInt("latitude")
            val longitude = result.getInt("longitude")
            val date = result.getLong("date")
            resultList.add(Location(latitude, longitude, date))
        }
        return resultList.toList()
    }

    fun addContact(contactReceiveData: ContactReceiveData): Int? {
        val contactUser = contactReceiveData.login?.let { ServerData.usersList.getUser(it) } ?: return null
        if (this.contacts.any { userContact -> userContact.userId == contactUser.id })
            return null
        val contactName = contactReceiveData.name ?: contactUser.name
        val contactShowLocation = contactReceiveData.showLocation ?: true
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "insert into UserSavedContacts (userId, contactUserId, name, showLocation) values (?, ?, ?, ?) " +
                    "returning contactId;")
        query.setInt(1, id)
        query.setInt(2, contactUser.id)
        query.setString(3, contactName)
        query.setBoolean(4, contactShowLocation)
        val result = query.executeQuery()
        result.next()
        if (contactReceiveData.shareLocation == true)
            shareLocation(contactUser.id)
        val contactId = result.getInt("contactId")
        this.contactsMutable.add(Contact(contactId, contactUser.id, contactName, contactShowLocation))
        return contactId
    }

    fun addContactFromDb(contact: Contact) {
        this.contactsMutable.add(contact)
    }

    fun updateContact(contactReceiveData: ContactReceiveData,
                      writeToDb: Boolean = true,
                      usersList: UsersList = ServerData.usersList): Boolean {
        val contact = contacts.find { contact -> contact.contactId == contactReceiveData.contactId }
            ?: contacts.firstOrNull { contact ->
                contact.userId != null && contact.userId == contactReceiveData.login?.let { usersList.getUser(it)?.id }
            } ?: return false
        if (contactReceiveData.contactId != null && contactReceiveData.contactId != contact.contactId) return false
        if (contactReceiveData.login != null &&
            usersList.getUser(contactReceiveData.login)?.id != contact.userId) return false

        val contactName = contactReceiveData.name ?: contact.name
        val contactShowLocation = contactReceiveData.showLocation ?: contact.showLocation

        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "update UserSavedContacts set name = ?, showLocation = ? where contactId = ?;"
            )
            query.setString(1, contactName)
            query.setBoolean(2, contactShowLocation)
            query.setInt(3, contact.contactId)
            query.execute()
        }

        if (contactReceiveData.shareLocation != null) {
            if (contactReceiveData.shareLocation)
                contact.userId?.let { shareLocation(it) }
            else
                contact.userId?.let { stopSharingLocation(it) }
            contact.shareLocation = contactReceiveData.shareLocation
        }

        contact.name = contactName
        contact.showLocation = contactShowLocation
        return true
    }

    fun deleteContact(contactId: Int) {
        val contact = contacts.find { contact -> contact.contactId == contactId } ?: return

        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement("delete from UserSavedContacts where contactId = ?;")
        query.setInt(1, contactId)
        query.execute()

        contactsMutable.remove(contact)
    }

    fun jsonStringToSend(): String {
        var result = "{\"login\":\"$login\",\"name\":\"$name\",\"contacts\":["
        contacts.forEach { contact ->
            result += "{\"contactId\":${contact.contactId},"
            val user = contact.userId?.let { ServerData.usersList.getUser(it) }
            result += if (user != null)
                "\"login\":\"${user.login}\""
            else
                "\"login\":null"
            result += ",\"name\":\"${contact.name}\","
            result += "\"showLocation\":${contact.showLocation},"
            result += "\"shareLocation\":${contact.shareLocation}},"
        }
        if (contacts.isNotEmpty())
            result = result.substring(0, result.length - 1)
        result += "]}"
        return result
    }

    data class Contact(val contactId: Int,
                       val userId: Int?,
                       var name: String,
                       var showLocation: Boolean = true,
                       var shareLocation: Boolean = true)

    @Serializable
    data class ContactReceiveData(val contactId: Int? = null,
                                  val login: String? = null,
                                  val name: String? = null,
                                  val showLocation: Boolean? = null,
                                  val shareLocation: Boolean? = null)
}

class UsersList {
    private val map = mutableMapOf<Int, User>()
    val loginToIdMap = mutableMapOf<String, Int>()
    val connection: Connection = ServerData.databaseConnection

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

    fun deleteUser(id: Int): User? {
        val user = getUser(id)
        if (user != null) {
            val query = connection.prepareStatement("delete from \"User\" where id = ?;")
            query.setInt(1, id)
            query.execute()
            map.remove(id)
            loginToIdMap.remove(user.login)
        }
        return user
    }

    fun getList() = map.values

    companion object {
        fun readFromDatabase(connection: Connection = ServerData.databaseConnection): UsersList {
            val queryReadUsers = connection.prepareStatement("select * from \"User\";")
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

            val queryReadContacts = connection.prepareStatement(
                "select contactId, userId, contactUserId, name, showLocation from UserSavedContacts;")
            val resultReadContacts = queryReadContacts.executeQuery()
            while (resultReadContacts.next()) {
                val contactId = resultReadContacts.getInt("contactId")
                val userId = resultReadContacts.getInt("userId")
                val contactUserId = resultReadContacts.getInt("contactUserId")
                val name = resultReadContacts.getString("name")
                val showLocation = resultReadContacts.getBoolean("showLocation")
                usersList.getUser(userId)?.addContactFromDb(
                    User.Contact(contactId, contactUserId, name, showLocation, false))
            }

            val queryReadLocationSharing = connection.prepareStatement("select * from UserShareLocation;")
            val resultReadLocationSharing = queryReadLocationSharing.executeQuery()
            while (resultReadLocationSharing.next()) {
                val userSharingId = resultReadLocationSharing.getInt("userSharingId")
                val userSharedToId = resultReadLocationSharing.getInt("userSharedToId")
                val userSharing = usersList.getUser(userSharingId) ?: continue
                userSharing.shareLocation(userSharedToId, false)
                println("User sharing id = ${userSharing.id}\nUser shared to id = $userSharedToId")
                println(userSharing.updateContact(User.ContactReceiveData(
                    login = usersList.getUser(userSharedToId)?.login,
                    shareLocation = true
                ), false, usersList))
            }

            return usersList
        }
    }
}