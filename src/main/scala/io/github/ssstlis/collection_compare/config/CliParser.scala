package io.github.ssstlis.collection_compare.config

import scopt.{DefaultOParserSetup, OParser, OParserSetup}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object CliParser {

  private val builder = OParser.builder[CliConfig]

  private def parseList(s: String): List[String] =
    s.stripPrefix("(").stripSuffix(")").split(',').map(_.trim).filter(_.nonEmpty).toList

  private val parser: OParser[Unit, CliConfig] = {
    import builder._
    OParser.sequence(
      programName("compare-collections"),
      head("compare-collections", "0.1.0"),
      opt[String]("collection1")
        .required()
        .action((v, c) => c.copy(collection1 = v))
        .text("Name of the first MongoDB collection"),
      opt[String]("collection2")
        .required()
        .action((v, c) => c.copy(collection2 = v))
        .text("Name of the second MongoDB collection"),
      opt[String]("db1")
        .required()
        .action((v, c) => c.copy(db1 = v))
        .text("Name of the first MongoDB DB"),
      opt[String]("db2")
        .required()
        .action((v, c) => c.copy(db2 = v))
        .text("Name of the second MongoDB DB"),
      opt[String]("host1")
        .required()
        .action((v, c) => c.copy(host1 = Some(v)))
        .text("Named config key for collection1 (looks up mongodb.<key> in application.conf; default: \"default\")"),
      opt[String]("host2")
        .required()
        .action((v, c) => c.copy(host2 = Some(v)))
        .text("Named config key for collection2 (looks up mongodb.<key> in application.conf; default: \"default\")"),
      opt[String]("filter")
        .action((v, c) => c.copy(filter = v))
        .text("MongoDB filter as JSON string (default: {})"),
      opt[Int]("request_timeout")
        .action((v, c) => c.copy(requestTimeout = FiniteDuration(v, TimeUnit.SECONDS)))
        .text("Request timeout in seconds (default: 30)"),
      opt[String]("exclude-fields")
        .action((v, c) => c.copy(excludeFields = parseList(v)))
        .text("Comma-separated fields to exclude from comparison, e.g. time,periodId"),
      opt[String]("projection-exclude")
        .action((v, c) => c.copy(projectionExclude = parseList(v)))
        .text("Comma-separated fields to exclude from MongoDB projection (not fetched at all), e.g. largeField,blob"),
      opt[Int]("round-precision")
        .action((v, c) => c.copy(roundPrecision = v))
        .text("Decimal places to round numeric values to (default: 0)"),
      opt[String]("output-path")
        .action((v, c) => c.copy(outputPath = v))
        .text("Directory where reports will be written (default: .)"),
      opt[String]("key")
        .action((v, c) => c.copy(key = parseList(v)))
        .text("Key field(s) for document matching and always-keep in cut report, e.g. _id or (_id,date)"),
      opt[String]("exclude-from-cut")
        .action((v, c) => c.copy(excludeFromCut = parseList(v)))
        .text("Fields to always keep in the cut report even if they have no differences"),
      opt[String]("sort")
        .action((v, c) => c.copy(sortBy = SortSpec.parseAll(v)))
        .text("Sort order for result files. Format: [abs_]<field>_(diff|1|2) [asc|desc], ... " +
          "e.g. \"abs_pnl_diff desc, abs_fee_diff desc\". Overrides default sort by totalDiffScore."),
      opt[String]("reports")
        .action((v, c) => c.copy(reports = parseList(v).map(ReportType.parse(_).get).distinct))
        .text("Report that being generated.Available options: (csv, json, excel)."),
      opt[String]("formula_delim")
        .action((v, c) => c.copy(excelFormulasDelimiter = ExcelFormulaSeparator.parse(v).get))
        .text("Formula arguments delimiter that being used.Available options: (comma, semicolon)."),
    )
  }

  def parse(args: Array[String]): Option[CliConfig] = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError = Some(true)
      override def errorOnUnknownArgument: Boolean = false
    }
    OParser.parse(parser, args, CliConfig(), setup)
  }
}
