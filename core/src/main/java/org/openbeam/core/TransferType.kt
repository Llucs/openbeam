package org.openbeam.core

/**
 * Supported transfer types for OpenBeam sessions. This can be extended to include future types such
 * as streaming or text messages.
 */
enum class TransferType {
    FILE,
    MULTIPLE_FILES
}