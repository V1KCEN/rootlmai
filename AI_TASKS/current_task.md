# TASK

Исправить только поведение наковальни при объединении зачарований.

Требования:

- Sharpness I + Sharpness I → Sharpness II.
- Sharpness II + Sharpness II → Sharpness II.
- Sharpness II + Sharpness II + Unbreaking I → Sharpness II + Unbreaking I.
- Все остальные допустимые чары сохраняются.
- Уже существующий Sharpness III/V не понижать.
- Уже существующие Mending/Infinity/Flame и другие запрещённые чары не удалять.
- Не создавать новый запрещённый чар.
- Не отменять всю операцию из-за превышения капа.
- Не менять другие системы ROOT IMPACT.

Измени код и после этого выполни:
.\gradlew clean build --no-daemon

Проект должен быть готов к:
.\gradlew runClient