package org.openbeam.core

import android.net.Uri

/**
 * Metadata describing a file or set of files to be transferred. This information is exchanged
 * after the encrypted handshake to prepare the transport layer.
 *
 * @param name Name of the file or group of files.
 * @param size Total size in bytes.
 * @param uris List of Android URIs referencing the content (content://, file://, etc.).
 */
data class TransferMetadata(
    val name: String,
    val size: Long,
    val uris: List<Uri>
)