package ru.wizand.safeorbit.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.wizand.safeorbit.data.repository.CommandRepositoryImpl
import ru.wizand.safeorbit.domain.repository.CommandRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommandModule {

    @Binds
    @Singleton
    abstract fun bindCommandRepository(
        impl: CommandRepositoryImpl
    ): CommandRepository
}
