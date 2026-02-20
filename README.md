# compare-collections

Инструмент для сравнения двух коллекций MongoDB и генерации отчётов о различиях.

## Возможности

- Сравнение документов по составному ключу из двух коллекций (в т.ч. из разных MongoDB-инстансов)
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

- JDK 11+
- SBT 1.x
- MongoDB (доступная по URI из конфига)

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

## Сборка

```bash
sbt compile
```

## Запуск

```bash
sbt "run --collection1 <col1> --collection2 <col2> [опции]"
```

### Параметры

| Параметр               | Обязательный | По умолчанию | Описание                                                                                                       |
|------------------------|--------------|--------------|----------------------------------------------------------------------------------------------------------------|
| `--collection1`        | да           | —            | Имя первой коллекции                                                                                           |
| `--collection2`        | да           | —            | Имя второй коллекции                                                                                           |
| `--host1`              | нет          | `default`    | Имя секции в `mongodb { ... }` для первой коллекции                                                            |
| `--host2`              | нет          | `default`    | Имя секции в `mongodb { ... }` для второй коллекции                                                            |
| `--filter`             | нет          | `{}`         | MongoDB-фильтр в виде JSON-строки                                                                              |
| `--exclude-fields`     | нет          | —            | Поля, исключаемые из сравнения (через запятую или `(f1,f2)`)                                                   |
| `--projection-exclude` | нет          | —            | Поля, исключаемые из MongoDB-проекции — не загружаются вовсе (через запятую или `(f1,f2)`)                     |
| `--round-precision`    | нет          | `0`          | Количество знаков после запятой при округлении числовых значений                                               |
| `--output-path`        | нет          | `.`          | Папка для сохранения отчётов                                                                                   |
| `--key`                | нет          | `_id`        | Ключевые поля для сопоставления документов; всегда сохраняются в `_cut`-отчёте (формат: `field` или `(f1,f2)`) |
| `--exclude-from-cut`   | нет          | —            | Поля, которые всегда остаются в `_cut`-отчёте, даже если не имеют различий                                     |
| `--sort`               | нет          | totalDiffScore desc | Порядок сортировки результатов. Формат: `[abs_]<field>_(diff\|1\|2) [asc\|desc]`, через запятую |

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

Для `_1` / `_2` все значения преобразуются в строку. Если обе строки парсятся как число — используется числовое сравнение; иначе — лексикографическое. Отсутствующее поле считается `""` (минимальным).

Направление по умолчанию — `desc`. Если `--sort` не передан, результаты сортируются по `totalDiffScore desc`.

```bash
--sort "abs_pnl_diff desc, abs_mfee_diff desc, abs_pfee_diff desc"
--sort "amount_1 asc"
--sort "fee_diff"
```

### Примеры

Простое сравнение двух коллекций:

```bash
sbt "run --collection1 users_old --collection2 users_new"
```

Коллекции из разных MongoDB-инстансов (каждый описан в `application.conf`):

```bash
# application.conf: mongodb { old { host = "mongo-old" ... } new { host = "mongo-new" ... } }
sbt "run \
  --collection1 FeeReport_old \
  --collection2 FeeReport_new \
  --host1 old \
  --host2 new \
  --output-path /tmp/reports"
```

Полный пример с фильтром, исключёнными полями и срезом:

```bash
sbt "run \
  --collection1 FeeReport_old \
  --collection2 FeeReport_new \
  --filter '{\"periodId\":\"2025-YTD\"}' \
  --exclude-fields time,periodId,to \
  --round-precision 0 \
  --key _id \
  --exclude-from-cut cpIdStr,currency \
  --output-path /tmp/reports"
```

Составной ключ (совпадение по нескольким полям):

```bash
sbt "run \
  --collection1 transactions_v1 \
  --collection2 transactions_v2 \
  --key '(_id,date)' \
  --round-precision 2 \
  --output-path ./reports"
```

## Структура отчётов

После запуска в `--output-path` создаётся папка вида `compare_YYYY-MM-DD_HH-mm-ss/` с **19 файлами**:

```
compare_2025-01-15_14-30-00/
├── all_results.csv                        # все документы (общие + only-in)
├── all_results.json
├── all_results.xlsx
├── no_diff_results.csv                    # общие документы без различий
├── no_diff_results.json
├── no_diff_results.xlsx
├── has_diff_results.csv                   # общие документы с различиями (по убыванию суммарного отклонения)
├── has_diff_results.json
├── has_diff_results.xlsx
├── has_diff_results_cut.csv               # то же, но колонки без различий удалены
├── has_diff_results_cut.json
├── has_diff_results_cut.xlsx
├── only-<host1>-<collection1>.csv         # документы, присутствующие только в первой коллекции
├── only-<host1>-<collection1>.json
├── only-<host1>-<collection1>.xlsx
├── only-<host2>-<collection2>.csv         # документы, присутствующие только во второй коллекции
├── only-<host2>-<collection2>.json
├── only-<host2>-<collection2>.xlsx
└── summary.json                           # общая статистика
```

> Имена `<host>` и `<collection>` берутся из параметров запуска; специальные символы (`.`, `:`, `/` и т.д.) заменяются
> на `_`.

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

`has_diff_results_cut.*` — те же данные, что в `has_diff_results.*`, но колонки удалены по следующему правилу:

- Удаляется колонка, если у **всех** документов в наборе `is_X_same = true`
- **Всегда сохраняются**: `_id`, поля из `--key`, поля из `--exclude-from-cut`

Позволяет сосредоточиться только на реально отличающихся полях при большом числе колонок.

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

`fieldFrequency` — количество документов с различием по каждому полю (по убыванию).
`onlyIn1Count` / `onlyIn2Count` — документы, найденные только в первой / второй коллекции.

## Структура проекта

```
src/main/scala/.../
├── CompareApp.scala          # точка входа
├── CliConfig.scala           # модель CLI-параметров
├── CliParser.scala           # scopt-парсер
├── MongoConfig.scala         # конфигурация MongoDB (host, user, password, pool, ...)
├── BsonFlattener.scala       # разворачивание вложенных BSON в dot notation
├── FieldComparator.scala     # сравнение и округление значений
├── ComparisonResult.scala    # модели данных
├── MongoService.scala        # подключение к MongoDB и выборка документов
├── DocumentProcessor.scala   # оркестрация сравнения + логика cut
├── CsvWriter.scala
├── JsonWriter.scala
├── ExcelWriter.scala
└── ReportOrchestrator.scala  # создание папки и запись всех отчётов
src/main/resources/
└── application.conf          # настройки MongoDB
```
