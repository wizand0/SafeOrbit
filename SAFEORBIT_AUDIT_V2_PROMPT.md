# Промпт для нового чата: SafeOrbit — продолжение аудита и исправлений

Работай только с проектом:
`D:\Android\AndroidProjects\SafeOrbit`

Проект CamWall не трогай ни при каких обстоятельствах. Не читай и не изменяй его файлы.

## Роль и правила

Ты — Senior Android/Kotlin engineer. Отвечай по-русски, технически и кратко.

Перед изменениями:

1. Выполни `git status`, `git diff --stat`, `git branch --show-current`.
2. Проанализируй существующее рабочее дерево. В нём уже есть незакоммиченные изменения предыдущей серии — не откатывай их и не смешивай с новой работой без проверки.
3. Создай отдельную ветку от текущего состояния, например `ai/safeorbit-audit-v2`, если текущая ветка не является рабочей веткой. Не работай напрямую в `master`.
4. Сначала покажи краткий план и дождись подтверждения перед изменением кода.

Не выполнять без отдельного подтверждения:

- `git push`;
- `git reset --hard`;
- `git clean -fd`;
- `git rebase`;
- изменение package name, signing config, production API URL;
- изменение Room migration policy;
- изменение версий Gradle, AGP, Kotlin или библиотек;
- публикацию Firebase Rules.

Не использовать глобальный Gradle. Для сборки использовать только:
`\.\gradlew.bat`

## Главный источник плана

Сначала прочитай:
`D:\Android\AndroidProjects\SafeOrbit\AUDIT_REPORT.md`

Это версия 2 отчёта аудита и план работ. Не перечитывай большие файлы без необходимости: сначала используй `git diff` и точечное чтение файлов, относящихся к выбранному этапу.

## Порядок работы

### Этап 0 — базовая проверка

1. Проверить состояние Git и текущую ветку.
2. Запустить:
   - `\.\gradlew.bat assembleDebug`
   - `\.\gradlew.bat testDebugUnitTest`
3. Если сборка падает, изучить лог, исправить причину и повторить не более трёх раз.
4. Найти merge-conflict markers во всех исходниках.
5. Не исправлять несвязанные проблемы молча.

### Этап 1 — P0: Firebase Rules и авторизация

Сначала найди фактический файл Firebase Rules и все операции с Firebase. Составь таблицу:

`операция → RTDB path → auth actor → требуемое правило`

Проверь:

- `servers/$serverId`;
- `servers/$serverId/code`;
- `servers/$serverId/location`;
- `clients/$uid/linked_servers/$serverId`;
- `server_commands/$serverId`;
- `.read`, `.write`, `.validate`;
- невозможность перечисления чужих serverId;
- защиту от подстановки чужого serverId;
- server owner и linked client сценарии.

Не меняй правила вслепую. До реализации подготовь краткое описание текущего и предлагаемого контракта. Обрати внимание: pairing-код должен использоваться для одноразовой привязки, а не постоянно передаваться в командах. Если требуется новая схема pairing или криптографического payload, сначала опиши совместимость со старой версией и миграцию.

### Этап 2 — секреты и PIN

Проверь:

- `EncryptedPreferencesManager`;
- PBKDF2 hash/verify;
- constant-time comparison;
- миграцию старого PIN;
- повреждение Keystore;
- отсутствие plaintext fallback;
- failed attempts и lockout;
- отсутствие PIN, code, UID, serverId и координат в логах.

Добавь unit-тесты для hash/verify/migration/lockout, если текущая архитектура позволяет. Не раскрывай реальные значения секретов в отчёте или выводе.

### Этап 3 — DI, lifecycle, Worker и Room

Проверь:

- отсутствие дублирующих Hilt bindings;
- `@HiltWorker` и `HiltWorkerFactory`;
- инъекцию `AppDatabase` и `FirebaseRepository`;
- отмену `serviceScope`;
- снятие Firebase listeners, location callbacks и sensor listeners;
- отсутствие закрытия общего singleton DB сервисом;
- unique WorkManager jobs;
- `BootReceiver` при отсутствии permissions/auth/serverId;
- `AppDatabase`: destructive migration, exportSchema и миграции.

Изменение migration policy требует отдельного согласования до внесения.

### Этап 4 — Android runtime и policy

Проверь на доступном API/устройстве:

- permissions location;
- foreground service type location;
- запуск FGS из допустимого состояния;
- Android 12+/14+ ограничения;
- FGS notification;
- idle/active GPS режимы;
- battery saver и offline Firebase;
- boot и revoked permissions;
- disclosure и соответствие Google Play.

### Этап 5 — UI и regression

Проверь безопасное переименование сервера:

- обновляется существующая Room-запись;
- не создаётся запись с `id = 0`;
- не затираются code, icon, location и другие поля;
- корректно обновляется Flow без искусственных задержек;
- валидируются длина, trim, control characters и дубликаты;
- одинаково работает переименование из списка и карты.

## Важные ограничения по текущему состоянию

В предыдущей серии уже изменены и ещё не закоммичены файлы SafeOrbit, включая:

- `LocationService.kt`;
- `FirebaseRepository.kt`;
- `EncryptedPreferencesManager.kt`;
- `FirebaseModule.kt`;
- `ClientViewModel.kt`;
- `ServerDao.kt`;
- `AndroidManifest.xml`;
- `ServerSettingsActivity.kt`;
- `IdleLocationWorker.kt`;
- ProGuard;
- удаление Device Admin receiver и `project_structure.txt`.

Сначала проверь эти изменения сборкой и diff. Не удаляй и не восстанавливай файлы без объяснения.

## Финальная проверка

После реализации выбранного этапа:

1. Запусти `\.\gradlew.bat assembleDebug`.
2. Запусти `\.\gradlew.bat testDebugUnitTest`.
3. Если затронут R8/ProGuard — запусти release-сборку.
4. Покажи:
   - изменённые файлы;
   - краткий список исправлений;
   - команды и результаты проверок;
   - нерешённые риски;
   - `git diff --stat` и краткий diff.
5. Делай небольшие атомарные коммиты только после проверки. Не выполняй push.

Начни с проверки состояния Git и базовой сборки. После этого покажи план конкретного этапа и дождись подтверждения на изменения.
