package io.github.ssstlis.collection_compare.config

import io.github.ssstlis.compare_collections.BuildInfo
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
      programName(BuildInfo.name),
      head(BuildInfo.name, BuildInfo.version),
      opt[Unit]("version")
        .action((_, c) => c)
        .text("Print version information and exit"),
      opt[String]("mode")
        .validate(v => RunMode.parse(v).map(_ => ()))
        .action((v, c) => c.copy(mode = RunMode.parse(v).getOrElse(RunMode.Remote)))
        .text("Run mode: remote (default, connects to MongoDB) or file (reads from local Extended JSON files)"),
      opt[String]("collection1")
        .required()
        .action((v, c) => c.copy(collection1 = v))
        .text("Name of the first collection (also used for output file naming)"),
      opt[String]("collection2")
        .required()
        .action((v, c) => c.copy(collection2 = v))
        .text("Name of the second collection (also used for output file naming)"),
      // ── remote-mode flags ────────────────────────────────────────────────
      opt[String]("db1")
        .action((v, c) => c.copy(db1 = v))
        .text("[remote] Name of the first MongoDB database"),
      opt[String]("db2")
        .action((v, c) => c.copy(db2 = v))
        .text("[remote] Name of the second MongoDB database"),
      opt[String]("host1")
        .action((v, c) => c.copy(host1 = Some(v)))
        .text("[remote] Named config key for collection1 (looks up mongodb.<key> in conf file; default: \"default\")"),
      opt[String]("host2")
        .action((v, c) => c.copy(host2 = Some(v)))
        .text("[remote] Named config key for collection2 (looks up mongodb.<key> in conf file; default: \"default\")"),
      opt[Unit]("keep")
        .action((_, c) => c.copy(keep = true))
        .text("[remote] Save fetched documents as Relaxed Extended JSON arrays alongside reports"),
      opt[String]("filter")
        .action((v, c) => c.copy(filter = v))
        .text("[remote] MongoDB filter as JSON string (default: {})"),
      opt[Int]("request_timeout")
        .action((v, c) => c.copy(requestTimeout = FiniteDuration(v, TimeUnit.SECONDS)))
        .text("[remote] Request timeout in seconds (default: 30)"),
      opt[String]("projection-exclude")
        .action((v, c) => c.copy(projectionExclude = parseList(v)))
        .text("[remote] Comma-separated fields to exclude from MongoDB projection (not fetched at all)"),
      // ── file-mode flags ──────────────────────────────────────────────────
      opt[String]("file1")
        .action((v, c) => c.copy(file1 = Some(v)))
        .text("[file] Path to a MongoDB Relaxed Extended JSON array file for collection1"),
      opt[String]("file2")
        .action((v, c) => c.copy(file2 = Some(v)))
        .text("[file] Path to a MongoDB Relaxed Extended JSON array file for collection2"),
      // ── common flags ─────────────────────────────────────────────────────
      opt[String]("exclude-fields")
        .action((v, c) => c.copy(excludeFields = parseList(v)))
        .text("Comma-separated fields to exclude from comparison, e.g. time,periodId"),
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
        .text(
          "Sort order for result files. Format: [abs_]<field>_(diff|1|2) [asc|desc], ... " +
            "e.g. \"abs_pnl_diff desc, abs_fee_diff desc\". Overrides default sort by totalDiffScore."
        ),
      opt[String]("reports")
        .action((v, c) => c.copy(reports = parseList(v).map(ReportType.parse(_).get).distinct))
        .text("Report formats to generate. Available options: (csv, json, excel)."),
      opt[String]("formula_delim")
        .action((v, c) => c.copy(excelFormulasDelimiter = ExcelFormulaSeparator.parse(v).get))
        .text("Formula arguments delimiter. Available options: (comma, semicolon).")
    )
  }

  def parse(args: Array[String]): Option[AppConfig] = {
    if (args.contains("--version")) {
      println(s"""|${BuildInfo.name} ${BuildInfo.version}
                  |  branch    : ${BuildInfo.buildBranch}
                  |  commit    : ${BuildInfo.buildCommit}
                  |  built     : ${BuildInfo.buildTime}
                  |  modified  : ${BuildInfo.modified}
                  |  build#    : ${BuildInfo.buildNumber}""".stripMargin)
      sys.exit(0)
    }

    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError                = Some(true)
      override def errorOnUnknownArgument: Boolean = false
    }
    OParser.parse(parser, args, CliConfig(), setup).flatMap(toAppConfig)
  }

  private def toAppConfig(raw: CliConfig): Option[AppConfig] = raw.mode match {
    case RunMode.Remote =>
      if (raw.db1.isEmpty || raw.db2.isEmpty) {
        System.err.println("Error: --db1 and --db2 are required in remote mode")
        None
      } else {
        Some(
          RemoteConfig(
            collection1 = raw.collection1,
            collection2 = raw.collection2,
            db1 = raw.db1,
            db2 = raw.db2,
            host1 = raw.host1.getOrElse("default"),
            host2 = raw.host2.getOrElse("default"),
            keep = raw.keep,
            filter = raw.filter,
            requestTimeout = raw.requestTimeout,
            projectionExclude = raw.projectionExclude,
            excludeFields = raw.excludeFields,
            roundPrecision = raw.roundPrecision,
            outputPath = raw.outputPath,
            reports = raw.reports,
            key = raw.key,
            excludeFromCut = raw.excludeFromCut,
            sortBy = raw.sortBy,
            excelFormulasDelimiter = raw.excelFormulasDelimiter
          )
        )
      }

    case RunMode.File =>
      if (raw.keep) System.err.println("Warning: --keep is ignored in file mode")
      (raw.file1, raw.file2) match {
        case (Some(f1), Some(f2)) =>
          Some(
            FileConfig(
              collection1 = raw.collection1,
              collection2 = raw.collection2,
              file1 = f1,
              file2 = f2,
              excludeFields = raw.excludeFields,
              roundPrecision = raw.roundPrecision,
              outputPath = raw.outputPath,
              reports = raw.reports,
              key = raw.key,
              excludeFromCut = raw.excludeFromCut,
              sortBy = raw.sortBy,
              excelFormulasDelimiter = raw.excelFormulasDelimiter
            )
          )
        case _ =>
          System.err.println("Error: --file1 and --file2 are required in file mode")
          None
      }
  }
}
