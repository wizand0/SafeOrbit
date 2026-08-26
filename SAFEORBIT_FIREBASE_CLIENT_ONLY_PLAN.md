# SafeOrbit — план перехода на безопасную схему Firebase без доверенного серверного компонента

Дата: 2026-08-26
Проект: SafeOrbit
Ветка реализации: `ai/safeorbit-firebase-audit`

## Ограничения и решение

- Старых клиентов нет; обратная совместимость с текущим QR-форматом `serverId|code` не требуется.
- Платный тариф Firebase и Cloud Functions пока не используются.
- Firebase Rules не могут безопасно выполнить полноценную одноразовую проверку секрета с атомарным consume без доверенного компонента.
- Поэтому на текущем этапе используется **ограниченно безопасная client-only схема**: pairing выполняется клиентом через RTDB transaction, а доступ после привязки ограничивается membership-записью.
- Это не заменяет trusted backend: одноразовый секрет и право на создание membership частично защищены клиентской логикой и RTDB Rules, но злоумышленник с доступом к клиентскому Firebase Auth и подходящему payload потенциально может попытаться обойти pairing-flow.

## Целевая схема данных текущего этапа

```text
servers/$serverId
  ownerUid: string
  pairing:
    tokenHash: string
    expiresAt: number
    consumed: boolean
  location:
    latitude: number
    longitude: number
    timestamp: number
  app_notifications/$eventId: ...

clients/$clientUid/linked_servers/$serverId
  linkedAt: number
  status: "active"

server_commands/$serverId/$commandId
  type: string
  payload: object
  senderUid: string
  createdAt: number
```

### Принцип pairing без backend

1. Сервер после регистрации генерирует криптографически случайный token.
2. В RTDB хранится только хэш token и срок действия; plaintext token показывается на сервере/в QR один раз.
3. Клиент передаёт `serverId` и token в pairing-операцию.
4. Клиент читает только необходимую pairing-информацию, без права перечислять `servers`.
5. Проверка срока, хэша и флага `consumed` выполняется в RTDB transaction.
6. При успешной операции token помечается использованным, затем создаётся membership клиента.
7. Дальнейшие команды используют membership и Firebase Auth UID, а не pairing token/code.

> Ограничение: RTDB Rules не являются доверенным вычислительным backend и не должны считаться полноценной защитой от клиента, контролирующего собственный код или сетевые запросы.

## Этап A — исправление блокирующих и подготовительных проблем

1. Сохранить исправление UTF-8 для `activity_server_settings.xml`.
2. Устранить оставшееся бессмысленное сравнение `Boolean` с `null` в `RoleSelectionActivity`.
3. Проверить, что Hilt не содержит лишнего Firebase binding; оставить один способ предоставления `FirebaseRepository`.
4. Не менять package name, signing config, production URL, версии Gradle/AGP/Kotlin/библиотек.
5. Зафиксировать базовую сборку и тесты после исправлений.

## Этап B — FirebaseRepository и модель регистрации сервера

1. Убрать plaintext `code` как постоянный credential.
2. Добавить генерацию token через `SecureRandom` с достаточной длиной.
3. Добавить хэширование token с domain separation/фиксированным алгоритмом.
4. Записывать `ownerUid` только после успешного Firebase Auth.
5. Записывать pairing token hash и `expiresAt`.
6. Запретить регистрацию с пустым UID, пустым server ID или повторной записью существующего сервера.
7. Использовать транзакцию/conditional create для предотвращения перезаписи чужого server ID.
8. Не писать token, tokenHash, UID, serverId и координаты в логи.

## Этап C — pairing client-only

1. Обновить QR/pairing payload на формат версии, например:

   ```text
   safeorbit://pair?v=2&server=<serverId>&token=<token>
   ```

2. Валидировать схему, длину, допустимые символы и отсутствие control characters.
3. Выполнять pairing только для текущего authenticated client UID.
4. Проверять `expiresAt` и `consumed`.
5. Выполнять check-and-consume через RTDB transaction.
6. Создавать membership только для собственного `clients/$uid/linked_servers/$serverId`.
7. При любой ошибке не оставлять частично созданную привязку; повторный запуск должен быть безопасным.
8. После pairing больше не хранить и не передавать token в командах.
9. При необходимости хранить локально только server ID и отображаемые пользовательские данные; секрет pairing token удалить после успешной привязки.

## Этап D — строгие правила Realtime Database

Файл Rules подготовить отдельно и не публиковать без отдельного подтверждения.

Требования:

- `servers` не должен быть читаемым целиком.
- `servers/$serverId` доступен только owner и linked client.
- `servers/$serverId/location`: write только owner; read только owner/linked client.
- `servers/$serverId/app_notifications`: write только owner; read только owner/linked client.
- `ownerUid` immutable после создания.
- Pairing-поля нельзя произвольно изменять клиентом после создания, кроме строго ограниченного consume-сценария.
- `clients/$uid/linked_servers` доступен только `auth.uid == $uid`.
- Нельзя разрешать клиенту записывать membership для произвольного UID.
- `server_commands/$serverId`: create только linked client; чтение/удаление ограничить owner/server workflow.
- Команда должна содержать `senderUid == auth.uid`, допустимый `type`, валидный `createdAt` и ограниченную структуру payload.
- `.validate` добавить для строк, чисел, диапазонов координат, timestamp и обязательных полей.
- `users/$uid`: оставить доступ только владельцу и добавить валидацию role.
- Не разрешать запись неизвестных/лишних полей там, где Rules позволяют проверить структуру.

Отдельно проверить, что правила не дают возможность перечислить server ID через parent read или запрос с wildcard.

## Этап E — команды и серверный listener

1. Изменить `CommandRepositoryImpl` и `CommandViewModel` так, чтобы команды не содержали pairing code/token.
2. Передавать типизированную команду с `senderUid`, `createdAt` и payload.
3. На сервере принимать только команды из своего `server_commands/$serverId` и проверять структуру.
4. Удалять обработанную команду только после успешной валидации и обработки.
5. Ограничить размер и диапазон настроек active/idle interval.
6. Убрать логи command ID, server ID, UID и значений секретов.
7. Проверить повторную доставку, offline queue и повторную обработку команды.

## Этап F — тестирование безопасности

Добавить/выполнить тесты, насколько позволяет текущая архитектура:

- генерация token непредсказуема и не повторяется;
- hash/verify корректны;
- истёкший token отклоняется;
- consumed token нельзя использовать повторно;
- другой client UID не получает membership;
- linked client читает только привязанный сервер;
- чужой server ID не раскрывается через список;
- чужой client UID нельзя подставить;
- команды без membership отклоняются;
- команда с неверным senderUid отклоняется;
- повторная доставка не вызывает повторного опасного действия;
- plaintext token/code отсутствует в логах и DTO команд.

Rules проверить в Firebase Emulator Suite, если она уже доступна локально. Установку новых системных пакетов без отдельного согласования не выполнять.

## Этап G — финальная проверка и коммиты

1. ` .\gradlew.bat assembleDebug`.
2. ` .\gradlew.bat testDebugUnitTest`.
3. При изменении ProGuard/R8 — ` .\gradlew.bat assembleRelease`.
4. Повторно найти merge-conflict markers.
5. Проверить diff только SafeOrbit.
6. Сделать небольшие атомарные коммиты после успешных проверок.
7. `git push` не выполнять.
8. Firebase Rules не публиковать без отдельного подтверждения.

## Что нужно сделать в дальнейшем с trusted backend

К этому вопросу необходимо вернуться до production-использования или расширения круга пользователей.

Рекомендуемый следующий уровень:

1. Подключить Cloud Functions или собственный backend.
2. Перенести проверку pairing token и атомарное consume в trusted component.
3. Брать UID из проверенного Firebase Auth context, а не из тела запроса.
4. Хранить только hash token, `expiresAt`, `consumedAt` и audit metadata.
5. Создавать membership только после серверной проверки.
6. Перейти от client-only transaction к серверной авторизации команд.
7. Настроить App Check, rate limiting, budget alerts и мониторинг злоупотреблений.

До выполнения этого этапа текущая схема должна считаться пригодной только для ограниченного debug/test использования на собственных устройствах.
