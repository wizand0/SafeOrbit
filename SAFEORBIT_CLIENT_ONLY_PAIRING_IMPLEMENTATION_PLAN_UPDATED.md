# SafeOrbit — обновленный план поэтапного завершения client-only pairing

## Цель

Завершить переход проекта SafeOrbit на временную client-only схему pairing без legacy-совместимости и без передачи pairing code/token в командах. Каждая стадия выполняется небольшим изолированным изменением, после проверки создаётся отдельный атомарный коммит. Firebase Rules не публиковать без отдельного подтверждения.

## Ограничения

- Работать только в `D:\Android\AndroidProjects\SafeOrbit`.
- CamWall не читать и не изменять.
- Не откатывать существующие незакоммиченные изменения.
- Перед каждой стадией выполнить `git status --short` и `git diff --stat`.
- Не изменять package name, signing config, production API URL, Room migrations и версии Gradle/плагинов без отдельного согласования.
- Сборка только через