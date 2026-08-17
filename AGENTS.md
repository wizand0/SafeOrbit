# AGENTS.md — SafeOrbit

Инструкции для AI-агентов и разработчиков, работающих с этим репозиторием.

## Тип проекта

- Android-приложение, **Kotlin** (100%), один модуль `:app`.
- UI: **XML-layouts + ViewBinding + Navigation Component**. **Jetpack Compose НЕ используется.**
- Архитектура: **MVVM + элементы Clean Architecture** (`data` / `domain` / `presentation`).
- DI: **Hilt** (через `kapt`). Room — через **KSP**.

## Зафиксированные версии (состояние на момент фиксации)

| Компонент | Версия | Где задано |
|---|---|---|
| Kotlin | 2.1.21 | `gradle/libs.versions.toml` |
| AGP | 8.8.2 | `gradle/libs.versions.toml` |
| Gradle wrapper | 8.10.2 | `gradle/wrapper/gradle-wrapper.properties` |
| KSP | 2.1.21-2.0.1 | корневой `build.gradle` / version catalog |
| Hilt | 2.56.2 | `libs.versions.toml` + корневой `build.gradle` |
| Room | 2.7.1 | `app/build.gradle` |
| Firebase BoM | 33.14.0 | `libs.versions.toml` |
| compileSdk / targetSdk | 35 | `app/build.gradle` |
| minSdk | 29 | `app/build.gradle` |
| Java source/target | 11 | `app/build.gradle` (`compileOptions` + `kotlinOptions.jvmTarget`) |
| Compose Compiler | **не используется** (Compose в проекте нет) | — |

## Матрица совместимости — НЕ НАРУШАТЬ

1. **Kotlin ↔ Compose Compiler.** Если в проект когда-либо добавляется Compose:
   с Kotlin 2.0+ компилятор Compose встроен в Kotlin
   (плагин `org.jetbrains.kotlin.plugin.compose`, версия = версии Kotlin).
   Правило: **«Kotlin X.Y.Z требует Compose Compiler X.Y.Z — не обновлять одно без другого»**.
   Сейчас Compose отсутствует; не добавлять зависимости Compose без обсуждения.
2. **Kotlin ↔ KSP.** Версия KSP обязана начинаться с версии Kotlin
   (сейчас `2.1.21-2.0.1` для Kotlin `2.1.21`). Обновлять Kotlin и KSP только парой.
3. **AGP ↔ Gradle.** AGP 8.8.x требует Gradle ≥ 8.10.2. Обновлять AGP и Gradle вместе,
   сверяясь с официальной матрицей совместимости.
4. **Hilt ↔ kotlinx-metadata-jvm.** Для `kapt` на Kotlin 2.1.x обязателен
   `kotlinx-metadata-jvm` (сейчас 0.9.0) — не удалять.
5. **Java toolchain.** `sourceCompatibility`/`targetCompatibility` = 11 и
   `kotlinOptions.jvmTarget` = '11' менять только синхронно. Gradle запускать на JDK 17–21;
   JDK 25 для Gradle 8.10.x не поддерживается. `JAVA_HOME` должен указывать на JBR/JDK 17–21
   (например, `C:\Program Files\Android\Android Studio\jbr` или отдельный JDK 21).
6. Перед обновлением любой зависимости из `libs.versions.toml` проверить совместимость
   с текущими Kotlin/AGP/compileSdk.

## Архитектура — где создавать новый код

- `app/src/main/java/ru/wizand/safeorbit/`:
  - `domain/model` — доменные модели (чистый Kotlin, без Android-зависимостей);
  - `domain/repository` — интерфейсы репозиториев;
  - `domain/usecase` — юз-кейсы (одна ответственность на класс);
  - `data/model` — DTO/сущности Firebase и Room;
  - `data/firebase`, `data/repository` — реализации репозиториев;
  - `di` — Hilt-модули (`RepositoryModule`, `UseCaseModule`, `FirebaseModule`, `CommandModule`);
  - `presentation/<feature>` — Activity + ViewModel по экранам
    (`client`, `server`, `security`, `role`, `common`);
  - `utils` — утилиты без состояния.
- Новые ViewModel — только через `@HiltViewModel` + `@Inject constructor`.
- Новые репозитории — интерфейс в `domain`, реализация в `data`, привязка в `di/RepositoryModule`.
- UI — только XML + ViewBinding; не смешивать с Compose.

## Правила Git

- **Никогда не работать в `master`/`main` напрямую.** Рабочие ветки:
  `ai/*`, `feature/*`, `fix/*` (например `ai/fix-build`, `fix/<issue>`).
- Перед изменениями: `git status`, `git diff`.
- Коммиты маленькие, атомарные: одно логическое изменение — один коммит.
- Формат сообщений — как принято в репозитории: краткое описание на английском
  (примеры из истории: `v 1.7.0 - fix reboot`, `v.1.6.8 MVVM`); допустим префикс
  типа изменения: `fix:`, `docs:`, `refactor:`.
- **Без явного подтверждения владельца ЗАПРЕЩЕНО:**
  `git push`, `git push --force`, `git reset --hard`, `git clean -fd`,
  `git rebase` (в т.ч. `-i`), `git commit --amend` чужих коммитов, `git stash drop`.
- Не удалять файлы без необходимости.

## Запрещено менять без подтверждения владельца

- `applicationId` / `namespace` (`ru.wizand.safeorbit`) и любые настройки package name;
- signing config, keystore, конфиги release-подписи;
- production API URLs (Firebase-проекты, эндпоинты Agora/Yandex MapKit);
- ключи в `local.properties` (`YANDEX_MAPKIT_API_KEY`, `AGORA_APP_ID`);
- Room-миграции и схему БД (версия `@Database`, сущности, migration-объекты);
- версии Gradle/AGP/Kotlin/плагинов (см. матрицу выше);
- пользовательские данные и поведение device-admin / foreground-сервисов.

## Обязательные проверки после ЛЮБЫХ изменений

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Обе команды должны завершаться успешно. При падении сборки — проанализировать лог,
исправить, повторить (до 3 попыток), затем сообщить о проблеме.

## Особенности окружения / сбои, о которых стоит знать

- Ключи и секреты читаются из `local.properties` (`YANDEX_MAPKIT_API_KEY`, `AGORA_APP_ID`) —
  файл не коммитится; без ключей сборка проходит, но соответствующие функции не работают.
- `google-services.json` привязан к пакету `ru.wizand.safeorbit` — менять вместе с
  `applicationId` нельзя.
- В `settings.gradle` присутствует мёртвый репозиторий
  `https://webrtc.github.io/webrtc-org/native/android/` и дубль `jitpack.io` — не ломает
  сборку, но замедляет резолв зависимостей.
- Сборка включает `splits` по ABI (`armeabi-v7a`, `arm64-v8a`, universal) — несколько APK
  на выходе это норма.
