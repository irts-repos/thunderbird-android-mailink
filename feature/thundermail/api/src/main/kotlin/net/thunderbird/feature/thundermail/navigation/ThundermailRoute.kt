package net.thunderbird.feature.thundermail.navigation

import kotlinx.serialization.Serializable
import net.thunderbird.core.ui.navigation.Route

@Serializable
sealed interface ThundermailRoute : Route {
    companion object {
        private const val BASE_PATH = "app://thundermail"
        const val THUNDERMAIL_ADD_ACCOUNT_ROUTE = "$BASE_PATH/add-account"
        const val SIGN_IN_WITH_THUNDERMAIL_ROUTE = "$BASE_PATH/sign-in"
        const val SCAN_QR_CODE_ROUTE = "$BASE_PATH/scan-qr-code"
        const val INCOMING_SETTINGS_ROUTE = "$BASE_PATH/incoming-settings"
        const val ACCOUNT_SETUP_ROUTE = "$BASE_PATH/account-setup"
        const val ACCOUNT_ID_ROUTE_PARAM = "accountId"
        const val PERMISSIONS_ROUTE = "$BASE_PATH/permissions/{$ACCOUNT_ID_ROUTE_PARAM}"
        const val ONBOARD_COMPLETE_ROUTE = "$BASE_PATH/onboard-complete"
        const val IMPORT_COMPLETED_ROUTE = "$BASE_PATH/import-completed"
    }

    @Serializable
    data object AddAccount : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = THUNDERMAIL_ADD_ACCOUNT_ROUTE
    }

    @Serializable
    data object SignInWithThundermail : ThundermailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = SIGN_IN_WITH_THUNDERMAIL_ROUTE
    }

    @Serializable
    data object ScanQrCode : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = SCAN_QR_CODE_ROUTE
    }

    @Serializable
    data object IncomingSettings : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = INCOMING_SETTINGS_ROUTE
    }

    @Serializable
    data class Permissions(val accountId: String) : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = PERMISSIONS_ROUTE
    }

    /** QR import finished. Closes the account setup flow and returns to the mailbox list. */
    @Serializable
    data object ImportCompleted : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = IMPORT_COMPLETED_ROUTE
    }

    @Serializable
    data class OnboardComplete(val accountId: String) : ThundermailRoute {
        override val basePath: String = BASE_PATH
        override fun route(): String = ONBOARD_COMPLETE_ROUTE
    }

    @Serializable
    data class AccountSetup(val accountId: String? = null) : ThundermailRoute {
        override val basePath: String = "app://account/setup"

        override fun route(): String = basePath
    }
}
