package ru.wizand.safeorbit.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 1.6 (аудит): @Provides для FirebaseRepository удалён.
 *
 * FirebaseRepository теперь имеет @Inject constructor + @Singleton
 * (см. data/firebase/FirebaseRepository.kt) и предоставляется Hilt
 * автоматически. Оставленный здесь @Provides создал бы дублирующийся
 * биндинг и ошибку сборки графа зависимостей.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule
