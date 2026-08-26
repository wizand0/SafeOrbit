# SafeOrbit — план поэтапного завершения client-only pairing

## Цель

Завершить переход проекта SafeOrbit на временную client-only схему pairing без legacy-совместимости и без передачи pairing code/token в командах. Каждая стадия выполняется небольшим изолированным изменением, после проверки создаётся отдельный атомарный коммит. Firebase Rules не публиковать без отдельного подтверждения.

## Ограничения

- Работать только в `D:\Android\AndroidProjects\SafeOrbit`.
- CamWall не читать и не изменять.
- Не откатывать существующие незакоммиченные изменения.
- Перед каждой стадией выполнить `git status --short` и `git diff --stat`.
- Не изменять package name, signing config, production API URL, Room migrations и версии Gradle/плагинов без отдельного согласования.
- Сборка только через `.gradlew.bat` из корня SafeOrbit.
- Не выполнять `git push`, `git reset --hard`, `git clean -fd`, `git rebase`.
- Firebase Rules подготовить локально, но не публиковать.
- После каждой стадии проверять diff и конфликтные маркеры.

## Общая целевая схема

```text
servers/$serverId
  ownerUid: string
  pairing:
    tokenHash: string
    expiresAt: number
    consumed: boolean
  location: object

clients/$clientUid/linked_servers/$serverId: true

server_commands/$serverId/$commandId
  type: request_location_update | update_settings
  created_at: number
  request_location_update: boolean
  update_settings:
    active_interval: number
    inactivity_timeout: number
```

Pairing token передаётся только в QR/ручной форме при первичном связывании. В RTDB хранится только SHA-256 hash. Привязка выполняется transaction с проверками hash, срока действия и `consumed == false`. После успешной транзакции token становится недействительным.

---

## Этап 0. Зафиксировать исходное состояние

### Задачи

1. Проверить текущую ветку, статус и существующий diff.
2. Убедиться, что изменённые файлы относятся только к SafeOrbit.
3. Проверить состояние ранее запущенной компиляции.
4. Не смешивать исправления из предыдущей серии с новыми коммитами без отдельного просмотра.

### Команды проверки

```powershell
git status --short
git branch --show-current
git diff --stat
git diff --check
```

### Результат

- Зафиксирован список уже изменённых файлов.
- Новые изменения начинаются после сохранения текущего состояния.
- Коммит: не создавать, если существующий diff содержит несколько независимых незавершённых серий. Сначала разделить изменения логически при просмотре.

---

## Этап 1. Завершить компиляцию после текущих изменений

### Задачи

1. Получить итог текущего `compileDebugKotlin`.
2. Исправить только ошибки компиляции, появившиеся из-за уже выполненного перехода команд.
3. Не расширять область изменений.

### Основные файлы

- `app/src/main/java/ru/wizand/safeorbit/utils/CryptoUtils.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/repository/CommandRepositoryImpl.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/repository/CommandRepository.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/usecase/RequestServerLocationUseCase.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/usecase/SendServerSettingsUseCase.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/client/commands/CommandViewModel.kt`
- При необходимости — `ServerViewModel.kt` и `FirebaseRepository.kt`.

### Проверки

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
git diff --check
```

### Коммит

`fix: завершить переход команд без pairing code`

---

## Этап 2. Проверить и стабилизировать pairing token

### Задачи

1. Проверить генерацию token через `SecureRandom`.
2. Проверить URL-safe Base64 без переносов.
3. Проверить hash через единый метод `CryptoUtils.sha256Hex`.
4. Проверить поля регистрации:
   - `ownerUid`;
   - `pairing/tokenHash`;
   - `pairing/expiresAt`;
   - `pairing/consumed`.
5. Удалить из новой схемы запись legacy-полей `owner` и `code`.
6. Проверить, что callback получает именно token, а не старый code.
7. Проверить обработку ошибок записи регистрации.
8. Убедиться, что token не выводится в обычный Logcat.

### Файлы

- `app/src/main/java/ru/wizand/safeorbit/data/firebase/FirebaseRepository.kt`
- `app/src/main/java/ru/wizand/safeorbit/utils/CryptoUtils.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/server/ServerViewModel.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/security/EncryptedPreferencesManager.kt` — только если потребуется переименование ключей или явное обозначение pairing token.

### Важное ограничение

Client-only transaction не делает pairing полностью доверенным: клиентское приложение потенциально может быть модифицировано. Схема предназначена для debug/test и собственных устройств до появления trusted backend.

### Проверки

- Статический поиск старых полей в новой регистрации.
- Проверка отсутствия логирования полного token.
- `compileDebugKotlin`.

### Коммит

`feat: добавить одноразовый client-only pairing token`

---

## Этап 3. Обновить pairing UI и QR

### Задачи

1. Заменить терминологию `code` на `pairing token` там, где это относится к pairing.
2. QR должен содержать формат:

```text
serverId|pairingToken
```

3. Клиент должен принимать QR через `split("|", limit = 2)`.
4. Не использовать чтение `servers/$serverId/code`.
5. Ручной ввод должен вызывать transaction pairing через `FirebaseRepository.pairClientToServer`.
6. После успешного pairing сохранять локально только `serverId`; pairing token не нужен для команд.
7. В локальной Room-модели временно оставить поле `code` только до отдельного этапа миграции, но не использовать его для авторизации команд или Firebase Rules.
8. Проверить, что существующая логика списка серверов, деталей, удаления, переименования, карты и уведомлений не потеряна.

### Файлы

- `app/src/main/java/ru/wizand/safeorbit/presentation/client/AddServerDialogFragment.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/client/ServerListFragment.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/client/QrScanActivity.kt` — если используется этим сценарием.
- `app/src/main/java/ru/wizand/safeorbit/presentation/server/ServerSettingsActivity.kt`
- `app/src/main/res/layout/dialog_add_server.xml` — если нужно изменить подпись поля.
- `app/src/main/res/layout/dialog_connection_info.xml` — если нужно изменить подпись поля.
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/ru/wizand/safeorbit/presentation/client/ClientViewModel.kt`

### Особая проверка

`ServerListFragment.kt` ранее был временно переписан упрощённо. Нужно восстановить/проверить прежнюю функциональность `showDetails`, карту, bottom sheet/side panel, редактирование имени и удаление. Не принимать упрощённую реализацию, если она меняет пользовательское поведение без согласования.

### Проверки

- Ручной просмотр diff файла.
- `compileDebugKotlin`.
- Статический поиск чтения `servers/.../code`.

### Коммит

`feat: обновить QR и ручной pairing через token`

---

## Этап 4. Удалить token из команд и локальных зависимостей команд

### Задачи

1. Проверить все отправители команд.
2. Проверить все обработчики команд.
3. Убедиться, что payload команд не содержит:
   - `code`;
   - `pairing_token`;
   - `token`;
   - секретные значения из EncryptedPreferences.
4. Ограничить payload допустимой структурой.
5. Добавить проверки диапазонов:
   - `active_interval > 0`;
   - `inactivity_timeout > 0`;
   - разумный верхний предел интервала.
6. Обеспечить удаление команды после обработки только после успешной валидации структуры.
7. Не логировать полный payload.

### Файлы

- `app/src/main/java/ru/wizand/safeorbit/presentation/client/commands/CommandViewModel.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/repository/CommandRepositoryImpl.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/repository/CommandRepository.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/usecase/RequestServerLocationUseCase.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/usecase/SendServerSettingsUseCase.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/server/LocationService.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/server/worker/IdleLocationWorker.kt` — только если он создаёт/обрабатывает команды.

### Проверки

```powershell
Get-ChildItem app/src/main/java -Recurse -Filter *.kt |
  Select-String -Pattern 'server_commands|"code"|pairing_token|send.*Command'
```

### Коммит

`security: исключить pairing token из команд`

---

## Этап 5. Пересмотреть шифрование уведомлений

### Задачи

1. Определить, нужен ли клиенту локальный секрет для расшифровки уведомлений.
2. Не использовать pairing token как постоянный секрет, если он одноразовый.
3. Выбрать один вариант и зафиксировать его до реализации:
   - отдельный долгоживущий client/server shared secret, передаваемый только при pairing;
   - Firebase-поля с зашифрованным ключом для membership;
   - временно оставить старую схему только для debug/test и явно пометить ограничение.
4. Не ломать существующую доставку уведомлений до согласования нового формата.
5. Если меняется формат, подготовить совместное изменение publish/observe/clear.

### Файлы для анализа и возможного изменения

- `app/src/main/java/ru/wizand/safeorbit/data/repository/NotificationRepositoryImpl.kt`
- `app/src/main/java/ru/wizand/safeorbit/domain/repository/NotificationRepository.kt`
- `app/src/main/java/ru/wizand/safeorbit/presentation/client/NotificationViewModel.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/service/NotificationLoggerService.kt`
- `app/src/main/java/ru/wizand/safeorbit/utils/CryptoUtils.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/ServerEntity.kt`
- `app/src/main/java/ru/wizand/safeorbit/data/ServerDao.kt`

### Важное решение

Этот этап нельзя закрывать механической заменой `code` на pairing token: token одноразовый и не должен использоваться для постоянного шифрования уведомлений.

### Коммит

После выбора схемы: `security: отделить ключ уведомлений от pairing token`

---

## Этап 6. Подготовить строгие RTDB Rules локально

### Задачи

Создать локальный файл правил без публикации. Правила должны обеспечить:

1. Аутентификация для всех защищённых путей.
2. Доступ к серверу только:
   - `ownerUid == auth.uid`; или
   - наличие `clients/auth.uid/linked_servers/serverId == true`.
3. Запрет чтения `pairing/tokenHash` клиентом.
4. Pairing-запись допускается только по узкому пути и только с допустимым переходом состояния.
5. Запрет повторного consume.
6. Команды доступны только связанному клиенту для записи и владельцу сервера для чтения/удаления.
7. Валидация типа и структуры команд.
8. Проверка допустимых диапазонов интервалов.
9. Валидация структуры location.
10. Запрет доступа к чужим `serverId` и чужим `clients`.
11. Отдельные правила для `app_notifications`.
12. `.validate` для обязательных полей и типов.

### Файл

- `D:\Android\AndroidProjects\SafeOrbit\firebase\database.rules.json`

Если каталог `firebase` отсутствует, создать его только в SafeOrbit. Не изменять `firebase.json` без проверки существующей конфигурации.

### Дополнительный файл

- `SAFEORBIT_FIREBASE_RULES_REVIEW.md` — краткое объяснение каждого защищённого пути, ограничений client-only схемы и сценариев проверки.

### Проверки

- JSON-синтаксис локальным доступным инструментом.
- Просмотр diff.
- Проверка сценариев: owner, linked client, чужой client, истёкший token, повторный consume, неверная команда.
- Не выполнять `firebase deploy`.

### Коммит

`security: подготовить строгие локальные Firebase Rules`

---

## Этап 7. Добавить unit-тесты

### Задачи

Добавить тесты для чистых компонентов, не требующих реального Firebase:

1. SHA-256 hash стабилен для одинакового token.
2. Разные token дают разные hash.
3. Base64 token имеет ожидаемую непустую длину.
4. Структура команды не содержит code/token.
5. Интервалы команд проходят/не проходят валидацию.
6. Истёкший pairing token отклоняется на уровне чистой логики.
7. `consumed == true` отклоняется.
8. Неверный hash отклоняется.

### Файлы

- `app/src/test/java/ru/wizand/safeorbit/utils/CryptoUtilsTest.kt`
- Новый файл при необходимости:
  `app/src/test/java/ru/wizand/safeorbit/security/PairingTokenTest.kt`
- Новый чистый production-класс при необходимости:
  `app/src/main/java/ru/wizand/safeorbit/data/security/PairingTokenUtils.kt`
- Новый тест структуры команд при необходимости:
  `app/src/test/java/ru/wizand/safeorbit/commands/CommandPayloadTest.kt`

### Ограничение

Реальную атомарность Firebase transaction unit-тестами без эмулятора доказать нельзя. Для неё нужна отдельная интеграционная проверка через Firebase Emulator, если окружение проекта это поддерживает. Не добавлять эмулятор и новые зависимости без согласования.

### Проверки

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

### Коммит

`test: добавить проверки pairing token и команд`

---

## Этап 8. Финальная сборка и статический аудит

### Задачи

1. Выполнить полную debug-сборку.
2. Выполнить unit-тесты.
3. Проверить XML и Kotlin-компиляцию.
4. Проверить отсутствие конфликтных маркеров.
5. Проверить остатки legacy-схемы.
6. Проверить отсутствие token/code в логах и командах.
7. Просмотреть итоговый diff относительно ветки.

### Команды

```powershell
.\gradlew.bat assembleDebug --no-daemon
.\gradlew.bat testDebugUnitTest --no-daemon
git diff --check
git diff --stat
git diff main...HEAD --stat
Get-ChildItem app firebase -Recurse -File -ErrorAction SilentlyContinue |
  Select-String -Pattern '<<<<<<<|=======|>>>>>>>|server_commands|pairing_token|tokenHash|"code"'
```

### Коммит

Если все проверки успешны и diff просмотрен:

`chore: проверить SafeOrbit после перехода на client-only pairing`

`git push` не выполнять.

---

## Этап 9. Отдельная проверка на устройствах

### Сервер

1. Установить чистую debug-сборку.
2. Зарегистрировать сервер.
3. Проверить появление `ownerUid`, hash, expiry и `consumed=false`.
4. Открыть QR/token.
5. Убедиться, что token не появляется в логах.

### Клиент

1. Установить чистую debug-сборку.
2. Сканировать QR.
3. Проверить успешную первую привязку.
4. Повторить pairing тем же token — должно быть отказано.
5. Использовать истёкший token — должно быть отказано.
6. Отправить запрос location и настройки — команды должны работать без code/token.
7. Проверить работу карты, уведомлений и удаления сервера.

### Ограничение

До trusted backend схема не считается production-безопасной. Перед расширением круга пользователей необходимо вернуть этот вопрос в работу и реализовать pairing через доверенный backend/Cloud Functions.

---

## Порядок коммитов

1. `fix: завершить переход команд без pairing code`
2. `feat: добавить одноразовый client-only pairing token`
3. `feat: обновить QR и ручной pairing через token`
4. `security: исключить pairing token из команд`
5. `security: отделить ключ уведомлений от pairing token`
6. `security: подготовить строгие локальные Firebase Rules`
7. `test: добавить проверки pairing token и команд`
8. `chore: проверить SafeOrbit после перехода на client-only pairing`

Каждый коммит создаётся только после проверки соответствующей стадии. Firebase Rules не публикуются автоматически.
