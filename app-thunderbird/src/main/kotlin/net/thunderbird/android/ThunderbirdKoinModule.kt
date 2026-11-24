package net.thunderbird.android

import app.k9mail.feature.widget.shortcut.LauncherShortcutActivity
import com.fsck.k9.AppConfig
import com.fsck.k9.DefaultAppConfig
import com.fsck.k9.activity.MessageCompose
import net.thunderbird.android.auth.TbOAuthConfigurationFactory
import net.thunderbird.android.dev.developmentModuleAdditions
import net.thunderbird.android.feature.featureModule
import net.thunderbird.android.featureflag.thunderbirdFeatureFlagModule
import net.thunderbird.android.provider.providerModule
import net.thunderbird.android.widget.provider.MessageListWidgetProvider
import net.thunderbird.android.widget.provider.UnreadWidgetProvider
import net.thunderbird.android.widget.widgetModule
import net.thunderbird.app.common.appCommonModule
import net.thunderbird.core.common.oauth.OAuthConfigurationFactory
import net.thunderbird.feature.mail.mailink.api.MailLinkClient
import net.thunderbird.feature.mail.mailink.api.MailLinkTokenStore
import net.thunderbird.feature.mail.mailink.internal.KeystoreMailLinkTokenStore
import net.thunderbird.feature.mail.mailink.internal.OkHttpMailLinkClient
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * IR Mail uses password (IMAP/SMTP) login only, against the company mail server.
 * OAuth is disabled by providing no OAuth configurations, so the account setup never offers OAuth2.
 * The OAuth feature modules are kept (not deleted) so they can be re-enabled by setting this to true.
 */
private const val OAUTH_ENABLED = false

val appModule = module {
    includes(thunderbirdFeatureFlagModule)
    includes(appCommonModule)

    includes(widgetModule)
    includes(featureModule)
    includes(providerModule)

    single(named("ClientInfoAppName")) { BuildConfig.CLIENT_INFO_APP_NAME }
    single(named("ClientInfoAppVersion")) { BuildConfig.VERSION_NAME }
    single<AppConfig> { appConfig }
    single<OAuthConfigurationFactory> {
        if (OAUTH_ENABLED) TbOAuthConfigurationFactory() else OAuthConfigurationFactory { emptyMap() }
    }

    // MailLink (see ADR irmail-0002). The client is created lazily and only used by the MailLink screen.
    single<MailLinkTokenStore> { KeystoreMailLinkTokenStore(androidContext()) }
    single<MailLinkClient> {
        OkHttpMailLinkClient(baseUrl = BuildConfig.MAILINK_API_BASE_URL, httpClient = OkHttpClient())
    }

    developmentModuleAdditions()
}

val appConfig = DefaultAppConfig(
    componentsToDisable = listOf(
        MessageCompose::class.java,
        LauncherShortcutActivity::class.java,
        UnreadWidgetProvider::class.java,
        MessageListWidgetProvider::class.java,
    ),
)
