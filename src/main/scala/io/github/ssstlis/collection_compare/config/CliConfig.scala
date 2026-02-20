package io.github.ssstlis.collection_compare.config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

/** Internal scopt accumulator. Not part of the public API — use [[AppConfig]] instead. */
private[config] case class CliConfig(
  mode:                   RunMode                = RunMode.Remote,
  host1:                  Option[String]         = None,
  host2:                  Option[String]         = None,
  db1:                    String                 = "",
  db2:                    String                 = "",
  collection1:            String                 = "",
  collection2:            String                 = "",
  file1:                  Option[String]         = None,
  file2:                  Option[String]         = None,
  keep:                   Boolean                = false,
  filter:                 String                 = "{}",
  requestTimeout:         Duration               = FiniteDuration(30, TimeUnit.SECONDS),
  excludeFields:          List[String]           = Nil,
  projectionExclude:      List[String]           = Nil,
  roundPrecision:         Int                    = 0,
  outputPath:             String                 = ".",
  reports:                List[ReportType]       = List(ReportType.Json),
  key:                    List[String]           = List("_id"),
  excludeFromCut:         List[String]           = Nil,
  sortBy:                 List[SortSpec]         = Nil,
  excelFormulasDelimiter: ExcelFormulaSeparator  = ExcelFormulaSeparator.Semicolon
)