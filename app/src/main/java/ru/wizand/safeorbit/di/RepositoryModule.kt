package ru.wizand.safeorbit.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.wizand.safeorbit.data.AppDatabase
import ru.wizand.safeorbit.data.repository.ServerRepositoryImpl
import ru.wizand.safeorbit.domain.repository.ServerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideServerRepository(db: AppDatabase): ServerRepository {
        return ServerRepositoryImpl(db)
    }
}
