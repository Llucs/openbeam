package org.openbeam.transport

import kotlinx.coroutines.flow.Flow

/**
 * Represents an established transport channel capable of sending and receiving arbitrary
 * byte arrays. Implementations must handle connection setup, encryption (if any) is done
 * at higher layers.
 */
interface TransportChannel {
    suspend fun send(data: ByteArray)
    fun receive(): Flow<ByteArray>
    suspend fun close()
}