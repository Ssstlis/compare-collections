package io.github.ssstlis.collection_compare.writer

import io.circe.{Json, Printer}
import io.circe.syntax._
import io.github.ssstlis.collection_compare.model.ComparisonReport

import java.nio.file.{Files, Path}

object SummaryWriter {

  def writeSummary(report: ComparisonReport, path: Path): Unit = {
    val total   = report.all.size
    val noDiff  = report.noDiff.size
    val hasDiff = report.hasDiff.size

    val fieldFreq = report.hasDiff
      .flatMap(_.fields.filter(!_.isSame).map(_.field))
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .toList
      .sortBy(-_._2)

    val numericScores = report.hasDiff.map(_.totalDiffScore).filter(_ > 0)
    val avg           = if (numericScores.nonEmpty) numericScores.sum / numericScores.size else 0.0
    val min           = if (numericScores.nonEmpty) numericScores.min else 0.0
    val max           = if (numericScores.nonEmpty) numericScores.max else 0.0

    val json = Json.obj(
      "totalDocs"      -> total.asJson,
      "noDiffCount"    -> noDiff.asJson,
      "hasDiffCount"   -> hasDiff.asJson,
      "onlyIn1Count"   -> report.onlyIn1.size.asJson,
      "onlyIn2Count"   -> report.onlyIn2.size.asJson,
      "numericDiff"    -> Json.obj("avg" -> avg.asJson, "min" -> min.asJson, "max" -> max.asJson),
      "fieldFrequency" -> Json.obj(fieldFreq.map { case (k, v) => k -> v.asJson }: _*)
    )

    Files.writeString(path, json.printWith(Printer.spaces2))
  }
}