# Developer Notes

## Сборка и запуск

```bash
sbt compile          # компиляция
sbt test             # запуск тестов
sbt stage            # сборка дистрибутива → target/universal/stage/
sbt universal:packageBin  # zip-архив → target/universal/

# запуск без сборки дистрибутива (для разработки)
sbt "run --collection1 c1 --collection2 c2 --db1 db --db2 db"

# деплой дистрибутива + симлинки на бинарники
sbt "deploy <deployPath> <linkPath>"
# пример:
sbt "deploy /opt/tools ~/.local/bin"
```

### Команда `deploy`

Реализация вынесена в `lazy val deployTask: Def.Initialize[InputTask[Unit]]`.
Подключение к любому проекту — одна строка в `.settings(...)`:
```scala
deploy := deployTask.evaluated
```

Принимает два аргумента через пробел (пути с пробелами не поддерживаются).

Что делает:
1. Вызывает `Universal/stage` (пересобирает при необходимости)
2. Удаляет `<deployPath>/<name>-<version>/` если существует
3. Копирует `target/universal/stage/` → `<deployPath>/<name>-<version>/` с `COPY_ATTRIBUTES`
   (сохраняются права доступа, включая execute-бит на `bin/*`)
4. Создаёт `<linkPath>/` если не существует
5. Для каждого файла из `bin/` (не `.bat` на Unix, только `.bat` на Windows) создаёт симлинк
   в `<linkPath>/`, предварительно удаляя старый если есть
6. Логирует путь деплоя + размер и каждый созданный симлинк
7. Выводит `[warn]` для всех директорий в `<deployPath>` старше 30 дней (по `lastModifiedTime`),
   отсортированных по убыванию возраста

Вспомогательные функции в `build.sbt`: `formatBytes`, `copyDir`, `dirSize`.

## Coverage

Запустить покрытие:
```bash
sbt coverage test coverageReport

# Красивый консольный отчёт (из корня проекта):
python3 ~/projects/helpers/scoverage-report.py
# или явно:
python3 ~/projects/helpers/scoverage-report.py target/scala-2.13/scoverage-report/scoverage.xml

# HTML-отчёт:
open target/scala-2.13/scoverage-report/index.html
```

### Текущие результаты (Feb 2026)

**Statement coverage: 29.1% · Branch coverage: 30.2%**

| Класс              | Stmts | Покрыто | %      | Тест-файл                    |
|--------------------|------:|--------:|-------:|------------------------------|
| BsonFlattener      |    32 |      32 | 100.0% | BsonFlattenerSpec            |
| SortSpec           |    79 |      79 | 100.0% | SortSpecSpec                 |
| FileDocFetcher     |    16 |      16 | 100.0% | FileDocFetcherSpec           |
| DocumentProcessor  |   168 |     154 |  91.7% | DocumentProcessorSpec        |
| FieldComparator    |    39 |      36 |  92.3% | FieldComparatorSpec          |
| BsonUtils          |    30 |      10 |  33.3% | частично через DocumentProcessor |
| CliParser          |    87 |       0 |   0.0% | нет                          |
| RunMode            |     9 |       0 |   0.0% | нет                          |
| ReportType         |    17 |       0 |   0.0% | нет                          |
| ExcelFormulaSep.   |    12 |       0 |   0.0% | нет                          |
| MongoConfig        |    67 |       0 |   0.0% | требует MongoDB              |
| MongoService       |    51 |       0 |   0.0% | требует MongoDB              |
| SavingDocFetcher   |    11 |       0 |   0.0% | нет                          |
| JsonWriter         |    98 |       0 |   0.0% | нет                          |
| CsvWriter          |    44 |       0 |   0.0% | нет                          |
| ExcelWriter        |   175 |       0 |   0.0% | нет                          |
| ReportOrchestrator |    39 |       0 |   0.0% | нет                          |
| DurationFormatter  |    68 |       0 |   0.0% | нет                          |
| CompareApp         |    75 |       0 |   0.0% | entry point, не тестируется  |

### Что не покрыто и почему

**Намеренно не тестируется:**
- `CompareApp` — точка входа; логика разнесена по другим классам
- `MongoService` / `MongoConfig` — требуют живой MongoDB; тестируются интеграционно
- `ExcelWriter` — сложные Apache POI формулы; требует визуальной проверки

**Стоит добавить тесты:**
- `CliParser` — парсинг аргументов, валидация mode/file1/file2/db
- `RunMode.parse` — ошибочные значения
- `JsonWriter` / `CsvWriter` — вывод на основе простых `DocumentResult`
- `BsonUtils` — оставшиеся типы (`BsonDateTime`, `BsonArray`)
- `SavingDocFetcher` — проверить, что файл создаётся корректно
- `ReportOrchestrator.makeDir` — создание директории
- `DurationFormatter` — форматирование Duration

---

## Структура кода

```
src/main/scala/io/github/ssstlis/collection_compare/
│
├── CompareApp.scala                  # Точка входа (App)
│   └── Pattern-match на AppConfig → создаёт фетчеры → вызывает DocumentProcessor → пишет отчёты
│
├── ReportOrchestrator.scala          # Создание timestamped-директории и запись всех отчётов
│   ├── makeDir(outputPath): Path     # создаёт compare_YYYY-MM-DD_HH-mm-ss/
│   └── write(report, cut, dir, ...) # пишет CSV/JSON/XLSX для 6 групп + summary.json
│
├── config/
│   ├── AppConfig.scala               # sealed abstract class AppConfig
│   │   ├── RemoteConfig              # --mode remote: host1/2, db1/2, keep, filter, ...
│   │   └── FileConfig                # --mode file: file1, file2
│   ├── CliConfig.scala               # private[config] scopt-аккумулятор (не public API)
│   ├── CliParser.scala               # scopt → CliConfig → toAppConfig → Option[AppConfig]
│   ├── RunMode.scala                 # Remote | File
│   ├── SortSpec.scala                # парсинг sort-спецификации
│   ├── ReportType.scala              # Csv | Json | Excel
│   └── ExcelFormulaSeparator.scala   # Comma | Semicolon
│
├── mongo/
│   ├── DocFetcher.scala              # trait: fetchDocs(name, filter, projection): Seq[Document]
│   ├── MongoService.scala            # реализация для MongoDB (с прогресс-баром)
│   ├── FileDocFetcher.scala          # реализация для локальных Extended JSON файлов
│   ├── SavingDocFetcher.scala        # обёртка: сохраняет fetchDocs → файл (для --keep)
│   ├── DocumentProcessor.scala       # compareCollections(AppConfig) + applyCut(...)
│   ├── BsonFlattener.scala           # {a: {b: 1}} → {"a.b": 1}
│   ├── BsonUtils.scala               # BsonValue → String
│   ├── FieldComparator.scala         # roundBson, compareValues, numericDiff
│   └── MongoConfig.scala             # загружает секцию mongodb.<key> из application.conf
│
├── model/
│   ├── ComparisonReport.scala        # all, noDiff, hasDiff, onlyIn1, onlyIn2
│   ├── DocumentResult.scala          # id, fields, hasDifferences, totalDiffScore
│   └── FieldResult.scala             # field, value1, value2, isSame, numericDiff
│
├── writer/
│   ├── JsonWriter.scala              # write(docs, path) + writeSummary(report, path)
│   ├── CsvWriter.scala               # write(docs, path)
│   └── ExcelWriter.scala             # write(docs, path) + writeHasDiff(docs, path, delim)
│
└── util/
    └── DurationFormatter.scala       # prettyPrint, prettyPrintCompact, prettyPrintTime
```

### Поток данных

```
args
  └─► CliParser.parse → Option[AppConfig]
                              │
         ┌────────────────────┤
         ▼                    ▼
   RemoteConfig           FileConfig
   MongoService ──┐    FileDocFetcher ──┐
   SavingDocFetcher (если --keep)       │
                  │                     │
                  ▼                     ▼
         DocumentProcessor.compareCollections(AppConfig)
                  │  fetchDocs через DocFetcher trait
                  │  BsonFlattener.flatten
                  │  FieldComparator.roundBson / compareValues / numericDiff
                  ▼
         ComparisonReport { all, noDiff, hasDiff, onlyIn1, onlyIn2 }
                  │
         DocumentProcessor.applyCut (убирает одинаковые колонки)
                  │
         ReportOrchestrator.write(dir)
                  ├─► JsonWriter
                  ├─► CsvWriter
                  └─► ExcelWriter
```

### Ключевые инварианты

- `BsonFlattener` всегда исключает `_id` на верхнем уровне; он появляется как `DocumentResult.id`
- `numericDiff` = v2 − v1 (знаковое); `totalDiffScore` = сумма |numericDiff|
- `applyCut` сохраняет поля из `key` (кроме `_id`) и `excludeFromCut` всегда
- `SavingDocFetcher` сохраняет файл при вызове `fetchDocs`, до сравнения
- `FileDocFetcher` игнорирует `filter` и `projectionExclude` — возвращает все документы из файла
- `CliParser.toAppConfig` возвращает `None` с сообщением в stderr при невалидных аргументах

### Формат файлов (--keep / --mode file)

MongoDB **Relaxed Extended JSON v2** — массив JSON-объектов:
```json
[
  {"_id": {"$oid": "..."}, "amount": 100.5, "ts": {"$date": "2025-01-01T00:00:00Z"}},
  {"_id": {"$oid": "..."}, "amount": 200.0}
]
```
Записывается через `JsonWriterSettings.builder().outputMode(JsonMode.RELAXED)`,
читается через `BsonDocument.parse(s"""{"__data__": $content}""")`.
