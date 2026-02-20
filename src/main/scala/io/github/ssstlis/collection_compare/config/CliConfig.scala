package io.github.ssstlis.collection_compare.config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

case class CliConfig(
  host1: Option[String] = None,
  host2: Option[String] = None,
  db1: String = "",
  db2: String = "",
  collection1: String = "",
  collection2: String = "",
  filter: String = "{}",
  requestTimeout: Duration = FiniteDuration(30, TimeUnit.SECONDS),
  excludeFields: List[String] = Nil,
  projectionExclude: List[String] = Nil,
  roundPrecision: Int = 0,
  outputPath: String = ".",
  reports: List[ReportType] = List(ReportType.Json),
  key: List[String] = List("_id"),
  excludeFromCut: List[String] = Nil,
  sortBy: List[SortSpec] = Nil,
  excelFormulasDelimiter: ExcelFormulaSeparator = ExcelFormulaSeparator.Semicolon
)
