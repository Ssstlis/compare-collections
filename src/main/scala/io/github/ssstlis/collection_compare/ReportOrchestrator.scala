package io.github.ssstlis.collection_compare

import io.github.ssstlis.collection_compare.config.ReportType.Raw
import io.github.ssstlis.collection_compare.config.{ExcelFormulaSeparator, ReportType}
import io.github.ssstlis.collection_compare.model.{ComparisonReport, DocumentResult}
import io.github.ssstlis.collection_compare.writer.{CsvWriter, ExcelWriter, JsonWriter, RawJsonWriter, SummaryWriter}

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReportOrchestrator {

  private val Fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

  /** Creates a timestamped output directory under `outputPath` and returns its path. */
  def makeDir(outputPath: String): Path = {
    val timestamp = LocalDateTime.now().format(Fmt)
    val dir       = Paths.get(outputPath, s"compare_$timestamp")
    Files.createDirectories(dir)
    dir
  }

  /** Writes all reports to `dir` and returns `dir`.
    *
    * @param report
    *   full comparison report
    * @param hasDiffCut
    *   has_diff documents with all-same columns stripped
    * @param dir
    *   pre-created output directory (use [[makeDir]] to create it)
    * @param reports
    *   report formats to generate
    * @param host1
    *   config key / label for the first source
    * @param collection1
    *   collection name for the first source
    * @param host2
    *   config key / label for the second source
    * @param collection2
    *   collection name for the second source
    */
  def write(
    report: ComparisonReport,
    hasDiffCut: List[DocumentResult],
    dir: Path,
    reports: List[ReportType],
    host1: String,
    collection1: String,
    host2: String,
    collection2: String,
    delim: ExcelFormulaSeparator
  ): Path = {
    println(s"\nWriting reports to: $dir")

    def writeAll(name: String, docs: List[DocumentResult], enrichedExcel: Boolean = false): Unit = {
      val reportsToRun = reports.flatMap {
        case ReportType.Raw => None
        case other          => Some(other)
      }

      reportsToRun.foreach {
        case ReportType.Csv   => CsvWriter.write(docs, dir.resolve(s"$name.csv"))
        case ReportType.Json  => JsonWriter.write(docs, dir.resolve(s"$name.json"))
        case ReportType.Excel =>
          val p = dir.resolve(s"$name.xlsx")
          if (enrichedExcel) ExcelWriter.writeHasDiff(docs, p, delim)
          else ExcelWriter.write(docs, p)
        case _ => ()
      }

      println(s"  Written $name.{${reportsToRun.map(_.ext).sorted.mkString(",")}} (${docs.size} records)")
    }

    def writeRawReport(
      name: String,
      diffs: List[DocumentResult],
      onlyIn1: List[DocumentResult],
      onlyIn2: List[DocumentResult]
    ): Unit = {
      RawJsonWriter.write(diffs, onlyIn1, diffs, dir.resolve(s"$name.json"))
      println(s"  Written $name.json (${diffs.size + onlyIn1.size + onlyIn2.size} records)")
    }

    writeAll("all_results", report.all)
    writeAll("no_diff_results", report.noDiff)
    writeAll("has_diff_results", report.hasDiff, enrichedExcel = true)
    writeAll("has_diff_results_cut", hasDiffCut)
    writeAll(s"only-${sanitize(host1)}-${sanitize(collection1)}", report.onlyIn1)
    writeAll(s"only-${sanitize(host2)}-${sanitize(collection2)}", report.onlyIn2)

    if (reports.contains(ReportType.Raw)) {
      writeRawReport("raw_results", report.hasDiff, report.onlyIn1, report.onlyIn2)
    }

    SummaryWriter.writeSummary(report, dir.resolve("summary.json"))
    println("  Written summary.json")

    dir
  }

  private def sanitize(s: String): String = s.replaceAll("[^a-zA-Z0-9_-]", "_")
}
