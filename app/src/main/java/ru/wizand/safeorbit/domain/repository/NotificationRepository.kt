package ru.wizand.safeorbit.domain.repository

import ru.wizand.safeorbit.data.model.AppNotification

/**
 * Транспорт уведомлений, перехваченных сервером.
 *
 * Хранение: Firebase RTDB, узел `servers/{serverId}/app_notifications/{pushId}`.
 * Содержимое шифруется AES-256-GCM ключом, деривированным из кода пары,
 * поэтому прочитать уведомления может только клиент, знающий код подключения.
 */
interface NotificationRepository {

    companion object {
        /** Максимум записей в Firebase; старые удаляются при переполнении. */
        const val MAX_STORED = 100

        /** Сколько последних уведомлений отображается в UI клиента. */
        const val VISIBLE_LIMIT = 30
    }

    /**
     * Публикация уведомления (сторона сервера).
     *
     * @param packageName пакет приложения-источника
     * @param appLabel видимое имя приложения
     * @param title заголовок уведомления
     * @param text текст уведомления
     * @param postTime время публикации исходного уведомления (epoch ms)
     */
    suspend fun publish(
        serverId: String,
        code: String,
        packageName: String,
        appLabel: String,
        title: String,
        text: String,
        postTime: Long
    ): Result<Unit>

    /**
     * Подписка на уведомления сервера (сторона клиента).
     * Записи расшифровываются; с неверным кодом возвращает пустой результат без ошибок.
     *
     * @return Flow со списком последних [VISIBLE_LIMIT] уведомлений (новые сверху).
     */
    fun observe(serverId: String, code: String): kotlinx.coroutines.flow.Flow<List<AppNotification>>

    /** Полная очистка узла уведомлений (может выполнить любая сторона, знающая код). */
    suspend fun clear(serverId: String, code: String): Result<Unit>
}
