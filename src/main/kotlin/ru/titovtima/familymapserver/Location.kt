package ru.titovtima.familymapserver

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.SQLException
import java.util.Date

@Serializable
data class Location(val latitude: Int, val longitude: Int, val date: Long) {
    constructor(latitude: Int, longitude: Int, date: Date): this(latitude, longitude, date.time)
}

@Serializable
data class UserLocationData(val userId: Int, val location: Location) {
    fun writeToDatabase(connection: Connection = ServerData.databaseConnection): Boolean {
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
}
