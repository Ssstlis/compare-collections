# CLAUDE.md — контекст для Claude Code

## Правило: документирование изменений

**При любых изменениях в коде обязательно обновлять:**
- `README.md` — пользовательская документация (флаги, примеры, форматы файлов, структура отчётов)
- `DEVELOP.md` — журнал изменений для разработчика (что изменилось и почему)

Это правило не опционально — изменение кода без обновления документации неполно.

## Что это за проект

CLI-инструмент на **Scala 2.13 + SBT** для сравнения двух MongoDB коллекций (или JSON-файлов)
и генерации отчётов в CSV / JSON / XLSX.

## Быстрый старт

```bash
sbt test                          # запустить тесты
sbt coverage test coverageReport  # тесты с покрытием
python3 ~/projects/helpers/scoverage-report.py  # coverage-отчёт в консоли
sbt compile                       # только компиляция
```

## Архитектура — ключевые точки

### Конфиг (config/)

`AppConfig` — **sealed ADT** (все подтипы в одном файле `AppConfig.scala`!):
- `RemoteConfig` — режим MongoDB: `host1/2`, `db1/2`, `keep`, `filter`, `requestTimeout`, `projectionExclude`
- `FileConfig` — режим файлов: `file1`, `file2`

`CliConfig` помечен `private[config]` — это внутренний scopt-аккумулятор, не публичный API.
`CliParser.parse(args)` → `Option[AppConfig]` — публичная точка входа.

`collection1`/`collection2` — абстрактные методы в базовом `AppConfig`; каждый подтип реализует их через конструктор-параметр. Они намеренно НЕ являются "общим" полем с дефолтом.

### Фетчеры (mongo/)

Все реализуют `DocFetcher`:
| Класс | Назначение |
|-------|-----------|
| `MongoService` | Подключается к MongoDB, показывает прогресс-бар |
| `FileDocFetcher` | Читает Extended JSON массив из файла; игнорирует filter/projection |
| `SavingDocFetcher` | Обёртка: вызывает underlying.fetchDocs, затем сохраняет результат в файл |

`DocumentProcessor.compareCollections(cfg: AppConfig)` извлекает `filter` и `projectionExclude` через `match` на `RemoteConfig`/`FileConfig` — они есть только в `RemoteConfig`.

### Отчёты

`ReportOrchestrator.makeDir(outputPath)` создаёт `compare_YYYY-MM-DD_HH-mm-ss/` и возвращает путь.
`ReportOrchestrator.write(report, cut, dir, ...)` пишет в уже созданную `dir`.
Порядок важен: `makeDir` → (опционально сохранить --keep файлы через SavingDocFetcher) → `write`.

### Extended JSON

Формат: **MongoDB Relaxed Extended JSON v2**.
- Запись: `JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()`
- Чтение: `BsonDocument.parse(s"""{"__data__": $jsonArrayContent}""")` + `getArray("__data__")`

## Типичные задачи

### Добавить новый флаг

1. Добавить поле в `CliConfig` (private аккумулятор) с дефолтом
2. Добавить `opt[...]("flag-name")` в `CliParser.parser`
3. Добавить поле в `RemoteConfig` или `FileConfig` (или оба), обновить `toAppConfig`
4. Использовать в `CompareApp` или `DocumentProcessor`

### Добавить новый формат отчёта

1. Добавить значение в `ReportType`
2. Добавить `case ReportType.MyFormat => MyWriter.write(...)` в `ReportOrchestrator.writeAll`
3. Реализовать `MyWriter`

### Добавить поддержку нового BSON-типа

1. `BsonUtils.bsonToString` — добавить case в `match`
2. `FieldComparator.roundBson` — обработать если нужно округление
3. `FieldComparator.numericDiff` — обработать если числовой тип

## Предупреждения компилятора (ожидаемые)

Три файла (`CommonConfig.scala`, `RemoteConfig.scala`, `FileConfig.scala`) содержат только
комментарии (без Scala-определений) — компилятор выдаёт "Found names but no class/trait/object".
Это нормально: файлы объясняют, что типы перенесены в `AppConfig.scala`.

## Тесты — где что

| Файл | Что тестирует |
|------|--------------|
| `BsonFlattenerSpec` | Рекурсивное разворачивание BSON в dot notation |
| `FieldComparatorSpec` | roundBson, compareValues, numericDiff |
| `DocumentProcessorSpec` | compareCollections (noDiff/hasDiff/onlyIn1/2), applyCut, сортировка |
| `SortSpecSpec` | Парсинг sort-спецификации |
| `FileDocFetcherSpec` | Чтение Extended JSON файлов; BSON-типы |

Тесты используют `fakeService` (заглушка DocFetcher) и `RemoteConfig` с dummy db1/db2.

## Что НЕ тестируется (и почему)

- `MongoService` / `MongoConfig` — требуют живой MongoDB
- `ExcelWriter` — сложные POI-формулы; проверяется вручную
- `CompareApp` — entry point; логика вынесена в тестируемые классы
- `SavingDocFetcher` — стоит добавить тест с tempFile
- `CliParser` — стоит добавить тест для валидации режимов

## Зависимости

```
mongo-scala-driver 5.6.3  →  MongoDB BSON + Reactive Streams драйвер
scopt 4.1.0               →  CLI parsing
cats-core 2.13.0          →  (минимально используется)
Apache POI 5.5.1          →  XLSX-генерация (poi + poi-ooxml)
progressbar 0.10.2        →  прогресс-бар при загрузке из MongoDB
logback + log4j-over-slf4j→  логирование
scoverage (плагин)        →  coverage
scalafmt (плагин)         →  форматирование (sbt fmt / sbt fmtCheck)
```

## Форматирование

```bash
sbt fmt       # scalafmt + scalafmtSbt
sbt fmtCheck  # проверка без изменений
```

Конфиг: `.scalafmt.conf` в корне.
