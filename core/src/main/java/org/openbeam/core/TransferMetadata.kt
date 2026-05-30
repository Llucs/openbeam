package org.openbeam.core

import android.net.Uri

data class TransferMetadata(
    val name: String,
    val size: Long,
    val uris: List<Uri>
)