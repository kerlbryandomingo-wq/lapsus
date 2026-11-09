package com.krlbryn.sophia.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.krlbryn.sophia.core.AiProvider
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.AnthropicProvider
import com.krlbryn.sophia.core.DemoProvider
import com.krlbryn.sophia.core.OpenAiProvider
import com.krlbryn.sophia.data.db.BookmarkDao
import com.krlbryn.sophia.data.db.DownloadDao
import com.krlbryn.sophia.data.db.HistoryDao
import com.krlbryn.sophia.data.db.SophiaDatabase
import com.krlbryn.sophia.data.db.ShortcutDao
import com.krlbryn.sophia.data.db.StudyNoteDao
import com.krlbryn.sophia.data.secure.KeystoreSecretStore
import com.krlbryn.sophia.data.secure.SecretStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SophiaDatabase =
        Room.databaseBuilder(context, SophiaDatabase::class.java, "sophia.db").build()

    @Provides fun bookmarkDao(db: SophiaDatabase): BookmarkDao = db.bookmarks()
    @Provides fun historyDao(db: SophiaDatabase): HistoryDao = db.history()
    @Provides fun downloadDao(db: SophiaDatabase): DownloadDao = db.downloads()
    @Provides fun shortcutDao(db: SophiaDatabase): ShortcutDao = db.shortcuts()
    @Provides fun noteDao(db: SophiaDatabase): StudyNoteDao = db.notes()

    @Provides @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("sophia_settings") }

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun aiProviders(http: OkHttpClient): Map<AiProviderId, @JvmSuppressWildcards AiProvider> = mapOf(
        AiProviderId.OPENAI to OpenAiProvider(http, AiProviderId.OPENAI),
        AiProviderId.COMPATIBLE to OpenAiProvider(http, AiProviderId.COMPATIBLE),
        AiProviderId.ANTHROPIC to AnthropicProvider(),
        AiProviderId.DEMO to DemoProvider(),
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun secretStore(impl: KeystoreSecretStore): SecretStore
}
