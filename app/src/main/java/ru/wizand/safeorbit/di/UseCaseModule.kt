package ru.wizand.safeorbit.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import ru.wizand.safeorbit.domain.repository.ServerRepository
import ru.wizand.safeorbit.domain.usecase.AddServerUseCase

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    fun provideAddServerUseCase(repository: ServerRepository): AddServerUseCase {
        return AddServerUseCase(repository)
    }
}
