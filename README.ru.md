# CrossbowSaveArrow

[![en](https://img.shields.io/badge/lang-English-blue)](README.md) [![ru](https://img.shields.io/badge/lang-Русский-green)](README.ru.md)

Мод для Hytale, сохраняющий стрелы в каждом арбалете индивидуально.

## Проблема

В ванильной Hytale **стрелы теряются при смене оружия**. Если зарядить 4 стрелы и переключиться на меч — стрелы пропадают. Ammo хранится как стат сущности (игрока), а не предмета. При снятии модификатора оружия `clamp(ammo, 0, 0) = 0` — стрелы потеряны навсегда.

## Возможности

- **Стрелы сохраняются при смене оружия** — каждый арбалет хранит свой счётчик стрел в metadata предмета
- **Отложенное списание стрел** — стрела удаляется из инвентаря только ПОСЛЕ увеличения ammo, предотвращая потерю при прерванной перезарядке
- **Без дублирования стрел** — ванильный возврат стрел при SwapFrom заблокирован
- **UUID трекинг** — каждый арбалет получает уникальный ID для надёжной идентификации
- **Персистентное хранение** — ammo сохраняется при: смене оружия, дропе, хранении в сундуке, торговле, перелогине
- **Нулевой оверхед** — событийная архитектура, без тик-поллинга

## Архитектура

4 миксина через Hyxin:

| Миксин | Цель | Метод | Назначение |
|--------|------|-------|------------|
| `StatModifiersManagerMixin` | `StatModifiersManager` | `@Inject RETURN recalculateEntityStatModifiers` | Маппинг entity, назначение UUID, восстановление ammo из metadata |
| `EntityStatMapMixin` | `EntityStatMap` | `@Overwrite addStatValue` | Отложенное удаление стрел после увеличения ammo, запись metadata при любом изменении |
| `ModifyInventoryInteractionMixin` | `ModifyInventoryInteraction` | `@Redirect x2 firstRun` | Отложить удаление стрел в ThreadLocal, блокировать возврат стрел при SwapFrom |
| `ItemStackMixin` | `ItemStack` | `@Overwrite isEquivalentType` | Игнорировать LoadedAmmo/CrossbowUUID при сравнении metadata, предотвращая cancelOnItemChange |

Разделяемое состояние между classloader'ами через `System.getProperties()` (Mixin classloader не видит non-mixin классы из того же JAR).

```
ItemStack metadata (источник правды)
+-- LoadedAmmo: float    -- текущее количество стрел
+-- CrossbowUUID: string -- уникальный идентификатор арбалета
```

## Требования

- **Hyxin** — загрузчик Mixin для Hytale
  - [CurseForge](https://www.curseforge.com/hytale/mods/hyxin)
  - [GitHub](https://github.com/Jenya705/Hyxin)

## Установка

### Одиночная игра

1. Скачайте `Hyxin.jar` и поместите в:
   ```
   UserData/EarlyPlugins/
   ```

2. Скачайте `CrossbowSaveArrow-x.x.x.jar` и поместите в **папку сохранения мира**:
   ```
   UserData/Saves/<ИмяМира>/earlyplugins
   ```
   (Создайте файл `earlyplugins` если его нет — это ZIP-архив с jar внутри)

3. Запустите игру и загрузите мир

> **Linux:** Папка должна называться именно `earlyplugins` (в нижнем регистре). Linux чувствителен к регистру, `EarlyPlugins` не сработает.

### Выделенный сервер

1. Скачайте `Hyxin.jar` и поместите в:
   ```
   <КореньСервера>/earlyplugins/
   ```

2. Скачайте `CrossbowSaveArrow-x.x.x.jar` и поместите в:
   ```
   <КореньСервера>/earlyplugins/
   ```

> **Linux:** Папка должна называться именно `earlyplugins` (в нижнем регистре).

3. Запустите сервер

## Changelog

### v0.0.2

Полный рерайт системы хранения ammo. 2 миксина -> 4 миксина.

**Новое:**
- `EntityStatMapMixin` — перезаписывает `addStatValue()` для списания стрел ПОСЛЕ увеличения ammo (отложенное удаление). Предотвращает потерю стрел при прерванной перезарядке. Записывает ammo в metadata арбалета при каждом изменении (зарядка/выстрел)
- `ItemStackMixin` — перезаписывает `isEquivalentType()` для игнорирования ключей `LoadedAmmo` и `CrossbowUUID`. Предотвращает срабатывание `cancelOnItemChange` при обновлении metadata
- UUID для каждого арбалета
- Проверка наличия Hyxin при запуске с ошибкой SEVERE в лог если не найден
- Разделяемое состояние между classloader'ами через `System.getProperties()` (ThreadLocal + WeakHashMap)

**Изменено:**
- `StatModifiersManagerMixin` — убран HEAD inject и все `WeakHashMap` трекинги (`lastCrossbowSlot`, `lastCrossbowAmmo`, `hasRestored`). Теперь только RETURN inject. Всегда восстанавливает ammo из metadata (не только при `currentAmmo == 0`)
- `ModifyInventoryInteractionMixin` — добавлен второй `@Redirect` для `removeItemStack` для отложенного удаления стрел
- Убраны все debug `LOGGER.info()` вызовы

**Исправлено:**
- Потеря стрел при прерванной перезарядке (стрелы удалялись до увеличения ammo)
- Ammo не восстанавливался при переключении между двумя арбалетами (старый код требовал `currentAmmo == 0`)
- Десинхронизация ammo при перелогине (стат загружался с диска, не 0, восстановление пропускалось)
- Цепочка перезарядки ломалась при записи metadata в активный слот хотбара (триггерило `cancelOnItemChange`)

### v1.0.0

Первый релиз. 2 миксина: `StatModifiersManagerMixin` (HEAD+RETURN), `ModifyInventoryInteractionMixin` (1 redirect).

## Совместимость

- Работает с ванильными арбалетами и любыми модовыми арбалетами, использующими стандартную систему стата Ammo
- Совместим с другими модами, не модифицирующими поведение ammo арбалетов

## Credits

- **Автор:** Morgott
- **Mixin Framework:** Hyxin by Jenya705 — [CurseForge](https://www.curseforge.com/hytale/mods/hyxin) | [GitHub](https://github.com/Jenya705/Hyxin)
