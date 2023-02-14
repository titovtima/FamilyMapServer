package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.SQLException
import java.util.Date

@Serializable
data class Location(val latitude: Int, val longitude: Int, val date: Long) {
    constructor(latitude: Int, longitude: Int, date: Date): this(latitude, longitude, date.time)
}

fun writeLocationToDatabase(userId: Int,
                            location: Location,
                            connection: Connection = ServerData.databaseConnection): Boolean {
    val writeQuery = connection.prepareStatement(
        "insert into UserLocation (userid, latitude, longitude, date) values (?, ?, ?, ?)")
    writeQuery.setInt(1, userId)
    writeQuery.setInt(2, location.latitude)
    writeQuery.setInt(3, location.longitude)
    writeQuery.setLong(4, location.date)
    return try {
        writeQuery.execute()
        true
    } catch (_: SQLException) {
        false
    }
}

fun deleteOldLocations(maxAge: Long = 1000*60*60*24*7, connection: Connection = ServerData.databaseConnection) {
    val now = Date().time
    val readQuery = connection.prepareStatement("select userId, date from UserLocation")
    val readResult = readQuery.executeQuery()

    while (readResult.next()) {
        val userId = readResult.getInt("userId")
        val date = readResult.getLong("date")
        if (date + maxAge < now) {
            val deleteQuery = connection.prepareStatement("delete from UserLocation where userid = ? and date = ?")
            deleteQuery.setInt(1, userId)
            deleteQuery.setLong(2, date)
            deleteQuery.execute()
        }
    }
}
