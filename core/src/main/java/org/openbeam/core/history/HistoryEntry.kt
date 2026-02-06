package org.openbeam.core.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records each transfer that occurs via OpenBeam. The direction indicates whether
 * this device sent or received the file(s).
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val size: Long,
    val timestamp: Long,
    val direction: String
)