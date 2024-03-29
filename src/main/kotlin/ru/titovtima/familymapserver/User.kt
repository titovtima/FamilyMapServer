package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import org.postgresql.util.PSQLException
import java.sql.Connection

@Serializable
data class UserRegistrationData(val login: String, val password: String, val name: String) {
    fun writeToDatabase(connection: Connection = ServerData.databaseConnection): Int {
        val encodedPassword = RSAEncoder.encode(password).toString()
        val writeQuery = connection.prepareStatement(
            "insert into users (login, password, name) values (?, ?, ?);")
        writeQuery.setString(1, login)
        writeQuery.setString(2, encodedPassword)
        writeQuery.setString(3, name)
        writeQuery.execute()

        val getResultQuery = connection.prepareStatement("select id from users where login = ?;")
        getResultQuery.setString(1, login)
        val result = getResultQuery.executeQuery()
        result.next()
        return result.getInt("id")
    }
}

class User (val id: Int, val login: String, private var password: String, private var name: String) {
    private var lastLocation: Location?
    private val contactsMutable = mutableListOf<Contact>()
    val contacts
    get() = contactsMutable.toList()

    init {
        val connection = ServerData.databaseConnection
        val queryGetLastLocation = connection.prepareStatement(
            "select latitude, longitude, date from user_location where user_id = ? and date = (" +
                    "select max(date) from user_location where user_id = ? group by user_id);")
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
        val query = connection.prepareStatement("update users set password = ? where id = ?;")
        query.setString(1, encodedPassword)
        query.setInt(2, id)
        query.execute()
        password = encodedPassword
    }

    fun getName() = name
    fun setName(name: String) {
        this.name = name
    }

    private fun writeShareLocationToDb(userId: Int) {
        val connection = ServerData.databaseConnection
        val queryShare = connection.prepareStatement(
            "insert into user_share_location (user_sharing_id, user_shared_to_id) values (?, ?);"
        )
        queryShare.setInt(1, id)
        queryShare.setInt(2, userId)
        try {
            queryShare.execute()
        } catch (_: Error) {}

        val queryDeleteAsk = connection.prepareStatement(
            "delete from user_ask_for_sharing_location where user_asked_for_id = ? and user_asking_id = ?;"
        )
        queryDeleteAsk.setInt(1, id)
        queryDeleteAsk.setInt(2, userId)
        queryDeleteAsk.execute()
    }

    private fun writeStopSharingLocationToDb(userId: Int) {
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "delete from user_share_location where user_sharing_id = ? and user_shared_to_id = ?;"
        )
        query.setInt(1, id)
        query.setInt(2, userId)
        query.execute()
    }

    private fun askForSharingLocation(userId: Int) {
        try {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "insert into user_ask_for_sharing_location (user_asking_id, user_asked_for_id) values (?, ?);"
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
            "select user_asking_id from user_ask_for_sharing_location where user_asked_for_id = ?;")
        query.setInt(1, id)
        val queryResult = query.executeQuery()
        while (queryResult.next()) {
            ServerData.usersList.getUser(queryResult.getInt("user_asking_id"))?.login?.let { resultList.add(it) }
        }
        return resultList.toList()
    }

    fun checkSharingLocation(userId: Int) = contacts.find { it.userId == userId }?.shareLocation == true

    fun getLastLocation() = lastLocation

    fun updateLastLocation(location: Location) {
        val lastLocation = this.lastLocation
        if (lastLocation == null || lastLocation.date < location.date) {
            this.lastLocation = location
        }
    }

    fun getLocationHistory(): List<Location> {
        val resultList = mutableListOf<Location>()
        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "select latitude, longitude, date from user_location where user_id = ? order by date")
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

    fun addContact(contactReceiveData: ContactReceiveData): Contact? {
        val contactUser = contactReceiveData.login?.let { ServerData.usersList.getUser(it) } ?: return null
        if (this.contacts.any { userContact -> userContact.userId == contactUser.id })
            return null
        val contactName = contactReceiveData.name ?: contactUser.name
        val contactShowLocation = contactReceiveData.showLocation ?: true

        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement(
            "insert into user_saved_contacts (owner_id, user_ref_to_id, name, show_location) values (?, ?, ?, ?) " +
                    "returning id;")
        query.setInt(1, id)
        query.setInt(2, contactUser.id)
        query.setString(3, contactName)
        query.setBoolean(4, contactShowLocation)
        val result = query.executeQuery()
        result.next()
        val contactId = result.getInt("id")

        val contactShareLocation = contactReceiveData.shareLocation ?: true
        try {
            if (contactShareLocation)
                writeShareLocationToDb(contactUser.id)
            if (contactShowLocation && !contactUser.checkSharingLocation(this.id))
                askForSharingLocation(contactUser.id)
        } catch (_: PSQLException) {}

        val contact = Contact(contactId, contactUser.id, contactName, contactShowLocation, contactShareLocation)
        this.contactsMutable.add(contact)
        return contact
    }

    fun addContactFromDb(contact: Contact) {
        this.contactsMutable.add(contact)
    }

    fun updateContact(contactReceiveData: ContactReceiveData,
                      writeToDb: Boolean = true,
                      usersList: UsersList = ServerData.usersList): Boolean {
        val contact = contacts.find { contact -> contact.contactId == contactReceiveData.contactId }
            ?: contacts.firstOrNull { contact -> contact.userId != null &&
                    contact.userId == contactReceiveData.login?.let { usersList.getUser(it)?.id }
            }
            ?: return false

        if (contactReceiveData.contactId != null && contactReceiveData.contactId != contact.contactId) return false
        if (contactReceiveData.login != null &&
            usersList.getUser(contactReceiveData.login)?.id != contact.userId) return false

        val contactName = contactReceiveData.name ?: contact.name
        val contactShowLocation = contactReceiveData.showLocation ?: contact.showLocation

        if (writeToDb) {
            val connection = ServerData.databaseConnection
            val query = connection.prepareStatement(
                "update user_saved_contacts set name = ?, show_location = ? where id = ?;"
            )
            query.setString(1, contactName)
            query.setBoolean(2, contactShowLocation)
            query.setInt(3, contact.contactId)
            query.execute()

            if (contactReceiveData.shareLocation != null && contactReceiveData.shareLocation != contact.shareLocation) {
                if (contactReceiveData.shareLocation)
                    contact.userId?.let { writeShareLocationToDb(it) }
                else
                    contact.userId?.let { writeStopSharingLocationToDb(it) }
            }
        }

        if (contactReceiveData.shareLocation != null) {
            contact.shareLocation = contactReceiveData.shareLocation
        }
        val contactUser = contact.userId?.let { usersList.getUser(it) }
        if (contactUser != null && contactShowLocation && !contactUser.checkSharingLocation(this.id))
            askForSharingLocation(contactUser.id)

        contact.name = contactName
        contact.showLocation = contactShowLocation
        return true
    }

    private fun deleteContact(contactId: Int) {
        val contact = contacts.find { contact -> contact.contactId == contactId } ?: return

        val connection = ServerData.databaseConnection
        val query = connection.prepareStatement("delete from user_saved_contacts where id = ? and owner_id = ?;")
        query.setInt(1, contactId)
        query.setInt(2, this.id)
        query.execute()

        if (contact.shareLocation && contact.userId != null) {
            val queryDeleteSharing = connection.prepareStatement(
                "delete from user_share_location where user_sharing_id = ? and user_shared_to_id = ?;")
            queryDeleteSharing.setInt(1, this.id)
            queryDeleteSharing.setInt(2, contact.userId)
            queryDeleteSharing.execute()
        }

        contactsMutable.remove(contact)
    }

    fun deleteContact(contactReceiveData: ContactReceiveData): Boolean {
        val contact = contacts.find { contact -> contact.contactId == contactReceiveData.contactId }
            ?: contacts.find { contact -> contact.userId != null &&
                    contact.userId == contactReceiveData.login?.let { ServerData.usersList.getUser(it)?.id }
            }
            ?: return false
        if (contactReceiveData.contactId != null && contact.contactId != contactReceiveData.contactId) return false
        if (contactReceiveData.login != null &&
            ServerData.usersList.getUser(contactReceiveData.login)?.id != contact.userId) return false
        deleteContact(contact.contactId)
        return true
    }

    fun jsonStringToSend(): String {
        var result = "{\"login\":\"$login\",\"name\":\"$name\",\"contacts\":["
        contacts.forEach { contact ->
            result += contact.toJsonString() + ","
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
                       var shareLocation: Boolean = true) {
        fun toJsonString(): String {
            var string = "{\"contactId\":$contactId,"
            val user = userId?.let { ServerData.usersList.getUser(it) }
            string += if (user != null)
                "\"login\":\"${user.login}\","
            else
                "\"login\":null,"
            string += "\"name\":\"$name\",\"showLocation\":$showLocation,\"shareLocation\":$shareLocation}"
            return string
        }
    }

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
            val query = connection.prepareStatement("delete from users where id = ?;")
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
            val queryReadUsers = connection.prepareStatement("select * from users;")
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
                "select id, owner_id, user_ref_to_id, name, show_location from user_saved_contacts;")
            val resultReadContacts = queryReadContacts.executeQuery()
            while (resultReadContacts.next()) {
                val contactId = resultReadContacts.getInt("id")
                val userId = resultReadContacts.getInt("owner_id")
                val contactUserId = resultReadContacts.getInt("user_ref_to_id")
                val name = resultReadContacts.getString("name")
                val showLocation = resultReadContacts.getBoolean("show_location")
                usersList.getUser(userId)?.addContactFromDb(
                    User.Contact(contactId, contactUserId, name, showLocation, false))
            }

            val queryReadLocationSharing = connection.prepareStatement("select * from user_share_location;")
            val resultReadLocationSharing = queryReadLocationSharing.executeQuery()
            while (resultReadLocationSharing.next()) {
                val userSharingId = resultReadLocationSharing.getInt("user_sharing_id")
                val userSharedToId = resultReadLocationSharing.getInt("user_shared_to_id")
                val userSharing = usersList.getUser(userSharingId) ?: continue
                userSharing.updateContact(User.ContactReceiveData(
                    login = usersList.getUser(userSharedToId)?.login,
                    shareLocation = true
                ), false, usersList)
            }

            return usersList
        }
    }
}