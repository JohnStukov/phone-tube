package app.phonetube.di

import android.content.Context
import app.phonetube.core.media.AuthRepository
import app.phonetube.core.media.YouTubeRepository
import app.phonetube.core.media.network.ConnectivityMonitor
import app.phonetube.core.media.pending.PendingActionsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideYouTubeRepository(
        @ApplicationContext context: Context
    ): YouTubeRepository = YouTubeRepository(context)

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context
    ): AuthRepository = AuthRepository.get(context)

    @Provides
    @Singleton
    fun provideConnectivityMonitor(
        @ApplicationContext context: Context
    ): ConnectivityMonitor = ConnectivityMonitor.get(context)

    @Provides
    @Singleton
    fun providePendingActionsRepository(
        @ApplicationContext context: Context
    ): PendingActionsRepository = PendingActionsRepository.get(context)
}
