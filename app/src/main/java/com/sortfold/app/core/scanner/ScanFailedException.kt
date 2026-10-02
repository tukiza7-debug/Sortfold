package com.sortfold.app.core.scanner

/**
 * Typed failure surfaced by the scanner so the UI can explain what went wrong
 * in plain language. The original cause is always preserved for the Error
 * Library; nothing is swallowed.
 */
class ScanFailedException(val reason: Reason, cause: Throwable? = null) :
    Exception("Scan failed: ${reason.name}", cause) {

    enum class Reason {
        /** The persisted tree grant was revoked (user cleared it in system settings). */
        PERMISSION_REVOKED,

        /** The documents provider rejected the URI or failed mid-query. */
        PROVIDER_ERROR,
    }
}
