package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.SQLException
import java.util.Date

@Serializable
data class Location(val latitude: Int, val longitude: Int, val date: Long) {
    constructor(latitude: Int, longitude: Int, date: Date): this(latitude, longitude, date.time)

    fun toByteArray(): ByteArray {
        val array = ByteArray(16)
        for (i in 0..3) array[i] = (latitude shr (i*8)).toByte()
        for (i in 0..3) array[i + 4] = (longitude shr (i*8)).toByte()
        for (i in 0..7) array[i + 8] = (date shr (i*8)).toByte()
        return array
    }
}

fun writeLocationToDatabase(userId: Int,
                            location: Location,
                            connection: Connection = ServerData.databaseConnection): Boolean {
    val writeQuery = connection.prepareStatement(
        "insert into user_location (user_id, latitude, longitude, date) values (?, ?, ?, ?)")
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
    val readQuery = connection.prepareStatement("select user_id, date from user_location")
    val readResult = readQuery.executeQuery()

    while (readResult.next()) {
        val userId = readResult.getInt("user_id")
        val date = readResult.getLong("date")
        if (date + maxAge < now) {
            val deleteQuery = connection.prepareStatement("delete from user_location where user_id = ? and date = ?")
            deleteQuery.setInt(1, userId)
            deleteQuery.setLong(2, date)
            deleteQuery.execute()
        }
    }
}
