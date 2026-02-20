# compare-collections

Инструмент для сравнения двух коллекций MongoDB (или предварительно сохранённых JSON-файлов) и генерации отчётов о различиях.

## Возможности

- Сравнение документов по составному ключу из двух коллекций (в т.ч. из разных MongoDB-инстансов)
- **Два режима работы**: подключение к MongoDB (`--mode remote`) или чтение из файлов (`--mode file`)
- Сохранение загруженных данных в виде Extended JSON файлов для повторного использования (`--keep`)
- Рекурсивное разворачивание вложенных документов (dot notation)
- Опциональное округление числовых значений перед сравнением
- Исключение произвольных полей из сравнения
- Фильтрация документов через произвольный MongoDB-фильтр
- Генерация отчётов в трёх форматах: **CSV**, **JSON**, **XLSX**
- Подсветка различий красным цветом в Excel-файле
- Отдельный «срезанный» вариант (`_cut`) с удалёнными одинаковыми колонками
- Отдельные файлы `only-<host>-<collection>.*` для документов, присутствующих только в одной коллекции
- `summary.json` со статистикой по полям и числовым отклонениям

## Требования

- JDK 11+ (для запуска)
- SBT 1.x (только для сборки)
- MongoDB (только для `--mode remote`)

## Конфигурация

Файл `src/main/resources/application.conf` содержит **именованные секции** подключений к MongoDB.
Каждая секция — полный набор настроек: хост, порт, credentials, database, пул.

```hocon
mongodb {

  # Используется по умолчанию (если --host1 / --host2 не переданы)
  default {
    host = "localhost"
    port = 27017
    user = ""        # оставить пустым для подключения без авторизации
    password = ""
    database = "mydb"
    maxPoolSize = 10
    waitQueueMultiple = 2
    zoneId = "UTC"
  }

  # Пример дополнительного подключения — активируется через --host1 prod
  # prod {
  #   host     = "prod-mongo.example.com"
  #   port     = 27017
  #   user     = "admin"
  #   password = "secret"
  #   database = "proddb"
  # }

}
```

Флаги `--host1` / `--host2` принимают **имя секции** (ключ в `mongodb { ... }`).
Каждая коллекция может использовать отдельное подключение с полным набором настроек.

### Использование собственного конфигурационного файла

Не изменяйте `src/main/resources/application.conf` напрямую и не коммитьте в него credentials.
Создайте файл в любом месте (например `~/my-mongo.conf`) и передайте его через `JAVA_OPTS`:

```bash
JAVA_OPTS="-Dconfig.file=/path/to/my-mongo.conf" \
  bin/compare-collections \
  --collection1 Report \
  --collection2 Report \
  --db1 prod \
  --db2 staging \
  --host1 prod \
  --host2 staging
```

Файл должен иметь ту же структуру, что и `application.conf`:

```hocon
mongodb {

  prod {
    host     = "mongo-prod.example.com"
    port     = 27017
    user     = "admin"
    password = "secret"
    database = "proddb"
  }

  staging {
    host     = "mongo-staging.example.com"
    port     = 27017
    user     = ""
    password = ""
    database = "stagingdb"
  }

}
```

> Секция `default` не обязательна, если всегда явно передаются `--host1` и `--host2`.

## Сборка и деплой

```bash
# Сборка дистрибутива в target/universal/stage/
sbt stage

# Сборка zip-архива дистрибутива в target/universal/
sbt universal:packageBin
```

### Деплой командой `deploy`

```bash
sbt "deploy <deployPath> <linkPath>"
```

Команда:
1. Запускает `sbt stage` (если нужно)
2. Копирует `target/universal/stage/` в `<deployPath>/compare-collections-<version>/` (с сохранением прав доступа)
3. Создаёт симлинки для всех бинарников из `bin/` в `<linkPath>/`
4. Выводит путь деплоя с объёмом данных и список прилинкованных файлов
5. Предупреждает о папках в `<deployPath>`, которые не обновлялись более 30 дней

```bash
sbt "deploy /opt/tools ~/.local/bin"
# [info] Deployed  : /opt/tools/compare-collections-0.1.0-SNAPSHOT  (42.3 MB)
# [info] Linked    : /home/user/.local/bin/compare-collections  →  /opt/tools/.../bin/compare-collections
# [warn] Stale deployments in /opt/tools (older than 30 days):
# [warn]   compare-collections-0.0.9  (45 days ago)
# [warn]   compare-collections-0.0.8  (92 days ago)
```

> Пути с пробелами не поддерживаются. На Windows линкуются только `.bat`-файлы.

## Запуск

```bash
bin/compare-collections --collection1 <col1> --collection2 <col2> [опции]
```

> Для разработки можно использовать `sbt "run ..."` напрямую без сборки дистрибутива.

## Режимы работы

### `--mode remote` (по умолчанию)

Подключается к двум экземплярам MongoDB и загружает данные напрямую.

```bash
bin/compare-collections \
  --collection1 Report_old \
  --collection2 Report_new \
  --db1 mydb \
  --db2 mydb
```

При использовании `--keep` загруженные документы сохраняются рядом с отчётами в виде файлов
`<host1>-<db1>-<collection1>.json` и `<host2>-<db2>-<collection2>.json` в формате MongoDB Relaxed Extended JSON v2.
Эти файлы можно затем использовать в режиме `--mode file`.

```bash
bin/compare-collections \
  --collection1 Report_old \
  --collection2 Report_new \
  --db1 mydb \
  --db2 mydb \
  --keep \
  --output-path /tmp/reports
# → создаст /tmp/reports/compare_YYYY-MM-DD_HH-mm-ss/default-mydb-Report_old.json
#           /tmp/reports/compare_YYYY-MM-DD_HH-mm-ss/default-mydb-Report_new.json
```

### `--mode file`

Читает документы из локальных файлов в формате MongoDB Relaxed Extended JSON v2 (массив объектов).
Подключение к MongoDB не требуется; флаги `--db1`, `--db2`, `--host1`, `--host2`, `--filter`,
`--projection-exclude`, `--request_timeout` игнорируются.

```bash
bin/compare-collections \
  --mode file \
  --collection1 Report_old \
  --collection2 Report_new \
  --file1 /tmp/reports/compare_2025-01-15_14-30-00/Report_old.json \
  --file2 /tmp/reports/compare_2025-01-15_14-30-00/Report_new.json
```

**Формат файла** — JSON-массив документов в MongoDB Relaxed Extended JSON v2:
```json
[
  {"_id": {"$oid": "507f1f77bcf86cd799439011"}, "amount": 100.5, "ts": {"$date": "2025-01-01T00:00:00Z"}},
  {"_id": {"$oid": "507f1f77bcf86cd799439012"}, "amount": 200.0, "ts": {"$date": "2025-01-02T00:00:00Z"}}
]
```

## Параметры

### Общие (применяются в обоих режимах)

| Параметр               | Обязательный | По умолчанию | Описание                                                                                                       |
|------------------------|--------------|--------------|----------------------------------------------------------------------------------------------------------------|
| `--collection1`        | да           | —            | Имя первой коллекции (используется для именования файлов отчётов)                                              |
| `--collection2`        | да           | —            | Имя второй коллекции (используется для именования файлов отчётов)                                              |
| `--mode`               | нет          | `remote`     | Режим работы: `remote` (подключение к MongoDB) или `file` (чтение из файлов)                                   |
| `--exclude-fields`     | нет          | —            | Поля, исключаемые из сравнения (через запятую или `(f1,f2)`)                                                   |
| `--round-precision`    | нет          | `0`          | Количество знаков после запятой при округлении числовых значений                                               |
| `--output-path`        | нет          | `.`          | Папка для сохранения отчётов                                                                                   |
| `--key`                | нет          | `_id`        | Ключевые поля для сопоставления документов; всегда сохраняются в `_cut`-отчёте (формат: `field` или `(f1,f2)`) |
| `--exclude-from-cut`   | нет          | —            | Поля, которые всегда остаются в `_cut`-отчёте, даже если не имеют различий                                     |
| `--sort`               | нет          | totalDiffScore desc | Порядок сортировки результатов. Формат: `[abs_]<field>_(diff\|1\|2) [asc\|desc]`, через запятую |
| `--reports`            | нет          | `json`       | Форматы отчётов: `csv`, `json`, `excel`                                                                        |
| `--formula_delim`      | нет          | `semicolon`  | Разделитель аргументов в Excel-формулах: `comma` или `semicolon`                                               |

### Флаги режима `remote`

| Параметр               | Обязательный | По умолчанию | Описание                                                                    |
|------------------------|--------------|--------------|-----------------------------------------------------------------------------|
| `--db1`                | да           | —            | Имя первой MongoDB-базы данных                                              |
| `--db2`                | да           | —            | Имя второй MongoDB-базы данных                                              |
| `--host1`              | нет          | `default`    | Имя секции в `mongodb { ... }` для первой коллекции                         |
| `--host2`              | нет          | `default`    | Имя секции в `mongodb { ... }` для второй коллекции                         |
| `--keep`               | нет          | `false`      | Сохранить загруженные документы как Extended JSON рядом с отчётами           |
| `--filter`             | нет          | `{}`         | MongoDB-фильтр в виде JSON-строки                                           |
| `--projection-exclude` | нет          | —            | Поля, исключаемые из MongoDB-проекции — не загружаются вовсе                |
| `--request_timeout`    | нет          | `30`         | Таймаут запроса к MongoDB в секундах                                        |

### Флаги режима `file`

| Параметр | Обязательный | Описание                                                               |
|----------|--------------|------------------------------------------------------------------------|
| `--file1`| да           | Путь к файлу с данными первой коллекции (MongoDB Relaxed Extended JSON)|
| `--file2`| да           | Путь к файлу с данными второй коллекции (MongoDB Relaxed Extended JSON)|

### Сортировка (`--sort`)

Каждый элемент сортировки — это имя колонки из CSV/XLSX-вывода с опциональным направлением:

| Шаблон                     | Смысл                                           |
|----------------------------|-------------------------------------------------|
| `<field>_diff [asc\|desc]` | сортировка по `numericDiff` (v2 − v1), только для числовых полей |
| `<field>_1 [asc\|desc]`    | сортировка по значению из первой коллекции      |
| `<field>_2 [asc\|desc]`    | сортировка по значению из второй коллекции      |
| `abs_<field>_diff desc`    | сортировка по `|numericDiff|`                   |
| `abs_<field>_1 desc`       | сортировка по `|value1|` (для числовых полей)   |

Метрика `_diff` осмысленна только для числовых полей — для остальных `numericDiff` всегда `0.0`.
Направление по умолчанию — `desc`. Если `--sort` не передан, результаты сортируются по `totalDiffScore desc`.

```bash
--sort "abs_pnl_diff desc, abs_mfee_diff desc, abs_pfee_diff desc"
--sort "amount_1 asc"
--sort "fee_diff"
```

## Примеры

**Простое сравнение (remote):**

```bash
bin/compare-collections \
  --collection1 users_old \
  --collection2 users_new \
  --db1 mydb \
  --db2 mydb
```

**Коллекции из разных MongoDB-инстансов:**

```bash
# my-mongo.conf: mongodb { old { host = "mongo-old" ... } new { host = "mongo-new" ... } }
JAVA_OPTS="-Dconfig.file=/path/to/my-mongo.conf" \
  bin/compare-collections \
  --collection1 Report_old \
  --collection2 Report_new \
  --db1 psm \
  --db2 psm \
  --host1 old \
  --host2 new \
  --output-path /tmp/reports
```

**Сохранить данные для повторного использования:**

```bash
# Шаг 1: загрузить из MongoDB и сохранить
JAVA_OPTS="-Dconfig.file=/path/to/my-mongo.conf" \
  bin/compare-collections \
  --collection1 Report \
  --collection2 Report \
  --db1 prod \
  --db2 staging \
  --host1 prod \
  --host2 staging \
  --keep \
  --output-path /tmp/reports

# Шаг 2: использовать сохранённые файлы (без подключения к MongoDB)
bin/compare-collections \
  --mode file \
  --collection1 Report \
  --collection2 Report \
  --file1 /tmp/reports/compare_2025-01-15_14-30-00/prod-prod-Report.json \
  --file2 /tmp/reports/compare_2025-01-15_14-30-00/staging-staging-Report.json \
  --round-precision 2
```

**Полный пример с фильтром:**

```bash
bin/compare-collections \
  --collection1 Report_old \
  --collection2 Report_new \
  --db1 psm \
  --db2 psm \
  --filter '{"periodId":"2025-YTD"}' \
  --exclude-fields time,periodId,to \
  --round-precision 0 \
  --key _id \
  --exclude-from-cut cpIdStr,currency \
  --output-path /tmp/reports
```

**Составной ключ (совпадение по нескольким полям):**

```bash
bin/compare-collections \
  --collection1 transactions_v1 \
  --collection2 transactions_v2 \
  --db1 mydb \
  --db2 mydb \
  --key '(_id,date)' \
  --round-precision 2 \
  --output-path ./reports
```

## Структура отчётов

После запуска в `--output-path` создаётся папка вида `compare_YYYY-MM-DD_HH-mm-ss/` с файлами:

```
compare_2025-01-15_14-30-00/
├── <host1>-<db1>-<collection1>.json           # [только при --keep] сырые данные коллекции 1
├── <host2>-<db2>-<collection2>.json           # [только при --keep] сырые данные коллекции 2
├── all_results.csv                            # все документы (общие + only-in)
├── all_results.json
├── all_results.xlsx
├── no_diff_results.csv                        # общие документы без различий
├── no_diff_results.json
├── no_diff_results.xlsx
├── has_diff_results.csv                       # общие документы с различиями
├── has_diff_results.json
├── has_diff_results.xlsx
├── has_diff_results_cut.csv                   # то же, но колонки без различий удалены
├── has_diff_results_cut.json
├── has_diff_results_cut.xlsx
├── only-<host1>-<collection1>.csv             # документы только в первой коллекции
├── only-<host1>-<collection1>.json
├── only-<host1>-<collection1>.xlsx
├── only-<host2>-<collection2>.csv             # документы только во второй коллекции
├── only-<host2>-<collection2>.json
├── only-<host2>-<collection2>.xlsx
└── summary.json                               # общая статистика
```

> В режиме `--mode file` имена `<host1>`/`<host2>` заменяются на `file`.

### Формат CSV / XLSX

Каждая строка — один документ. Колонки на каждое поле `X`:

| Колонка     | Описание                                |
|-------------|-----------------------------------------|
| `X_1`       | значение из первой коллекции            |
| `X_2`       | значение из второй коллекции            |
| `X_diff`    | `X_2 - X_1` (только для числовых полей) |
| `is_X_same` | `true` / `false`                        |

В XLSX строки с различиями выделяются красным фоном.

### `_cut`-вариант

`has_diff_results_cut.*` — те же данные, но колонки удалены по следующему правилу:

- Удаляется колонка, если у **всех** документов в наборе `is_X_same = true`
- **Всегда сохраняются**: `_id`, поля из `--key`, поля из `--exclude-from-cut`

### Формат Extended JSON файлов (`--keep` / `--mode file`)

Файлы записываются и читаются в формате **MongoDB Relaxed Extended JSON v2**.
Массив JSON-объектов, где типы BSON сохраняются через специальные ключи:

| Тип BSON   | Представление                                         |
|------------|-------------------------------------------------------|
| ObjectId   | `{"$oid": "507f1f77bcf86cd799439011"}`                |
| Date       | `{"$date": "2025-01-15T14:30:00Z"}`                   |
| Decimal128 | `{"$numberDecimal": "3.14"}`                          |
| Int32/Int64/Double | обычные JSON-числа: `42`, `3.14`              |

### Формат summary.json

```json
{
  "totalDocs": 1500,
  "noDiffCount": 1423,
  "hasDiffCount": 77,
  "onlyIn1Count": 5,
  "onlyIn2Count": 3,
  "numericDiff": {
    "avg": 1204.5,
    "min": 0.01,
    "max": 98432.0
  },
  "fieldFrequency": {
    "amount": 65,
    "fee": 42,
    "balance": 12
  }
}
```

## Структура проекта

```
src/main/scala/.../
├── CompareApp.scala           # точка входа
├── ReportOrchestrator.scala   # создание папки и запись всех отчётов
├── config/
│   ├── AppConfig.scala        # sealed ADT: AppConfig / RemoteConfig / FileConfig
│   ├── CliConfig.scala        # внутренний scopt-аккумулятор (не public API)
│   ├── CliParser.scala        # scopt-парсер → Option[AppConfig]
│   ├── RunMode.scala          # Remote | File
│   ├── SortSpec.scala
│   ├── ReportType.scala
│   └── ExcelFormulaSeparator.scala
├── mongo/
│   ├── DocFetcher.scala        # интерфейс получения документов
│   ├── MongoService.scala      # реализация для MongoDB
│   ├── FileDocFetcher.scala    # реализация для локальных Extended JSON файлов
│   ├── SavingDocFetcher.scala  # обёртка: сохраняет документы в файл после загрузки
│   ├── MongoConfig.scala
│   ├── DocumentProcessor.scala
│   ├── BsonFlattener.scala
│   ├── BsonUtils.scala
│   └── FieldComparator.scala
├── model/
│   ├── ComparisonReport.scala
│   ├── DocumentResult.scala
│   └── FieldResult.scala
└── writer/
    ├── JsonWriter.scala
    ├── CsvWriter.scala
    └── ExcelWriter.scala
src/main/resources/
└── application.conf            # настройки MongoDB
```
