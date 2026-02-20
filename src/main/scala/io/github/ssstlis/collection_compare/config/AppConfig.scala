package io.github.ssstlis.collection_compare.config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

/** Base type for mode-specific application configuration.
  *
  * `collection1`/`collection2` are declared as abstract so each subtype defines them
  * with its own concrete meaning (MongoDB collection names vs. output labels).
  */
sealed abstract class AppConfig {
  def collection1: String
  def collection2: String

  def excludeFields:          List[String]
  def roundPrecision:         Int
  def outputPath:             String
  def reports:                List[ReportType]
  def key:                    List[String]
  def excludeFromCut:         List[String]
  def sortBy:                 List[SortSpec]
  def excelFormulasDelimiter: ExcelFormulaSeparator
}

/** Configuration for `--mode remote`: connects to two MongoDB instances.
  *
  * Required: `collection1`, `collection2`, `db1`, `db2`.
  * All other parameters have sensible defaults.
  */
final case class RemoteConfig(
  // required
  collection1:            String,
  collection2:            String,
  db1:                    String,
  db2:                    String,
  // remote-specific (optional)
  host1:                  String                 = "default",
  host2:                  String                 = "default",
  keep:                   Boolean                = false,
  filter:                 String                 = "{}",
  requestTimeout:         Duration               = FiniteDuration(30, TimeUnit.SECONDS),
  projectionExclude:      List[String]           = Nil,
  // common (optional)
  excludeFields:          List[String]           = Nil,
  roundPrecision:         Int                    = 0,
  outputPath:             String                 = ".",
  reports:                List[ReportType]       = List(ReportType.Json),
  key:                    List[String]           = List("_id"),
  excludeFromCut:         List[String]           = Nil,
  sortBy:                 List[SortSpec]         = Nil,
  excelFormulasDelimiter: ExcelFormulaSeparator  = ExcelFormulaSeparator.Semicolon
) extends AppConfig

/** Configuration for `--mode file`: reads both collections from local Extended JSON files.
  *
  * Required: `collection1`, `collection2`, `file1`, `file2`.
  * `collection1`/`collection2` are used as labels for output file naming only.
  */
final case class FileConfig(
  // required
  collection1:            String,
  collection2:            String,
  file1:                  String,
  file2:                  String,
  // common (optional)
  excludeFields:          List[String]           = Nil,
  roundPrecision:         Int                    = 0,
  outputPath:             String                 = ".",
  reports:                List[ReportType]       = List(ReportType.Json),
  key:                    List[String]           = List("_id"),
  excludeFromCut:         List[String]           = Nil,
  sortBy:                 List[SortSpec]         = Nil,
  excelFormulasDelimiter: ExcelFormulaSeparator  = ExcelFormulaSeparator.Semicolon
) extends AppConfig