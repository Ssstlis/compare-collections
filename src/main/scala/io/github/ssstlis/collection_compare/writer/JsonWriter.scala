package io.github.ssstlis.collection_compare.writer

import io.github.ssstlis.collection_compare.model.{ComparisonReport, DocumentResult}

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Path

object JsonWriter {

  def write(results: List[DocumentResult], path: Path): Unit = {
    val bw = new BufferedWriter(new FileWriter(path.toFile))
    try {
      bw.write("[\n")
      results.zipWithIndex.foreach { case (doc, idx) =>
        bw.write("  {\n")
        bw.write(s"""    "_id": ${bsonJson(doc.id)},\n""")
        bw.write(s"""    "totalDiffScore": ${doc.totalDiffScore},\n""")
        bw.write(s"""    "hasDifferences": ${doc.hasDifferences},\n""")
        bw.write("""    "fields": [""" + "\n")
        doc.fields.zipWithIndex.foreach { case (fr, fi) =>
          bw.write("      {\n")
          bw.write(s"""        "field": ${jsonStr(fr.field)},\n""")
          bw.write(s"""        "value1": ${fr.value1.map(bsonJson).getOrElse("null")},\n""")
          bw.write(s"""        "value2": ${fr.value2.map(bsonJson).getOrElse("null")},\n""")
          bw.write(s"""        "isSame": ${fr.isSame},\n""")
          bw.write(s"""        "numericDiff": ${fr.numericDiff}\n""")
          bw.write("      }" + (if (fi < doc.fields.size - 1) "," else "") + "\n")
        }
        bw.write("    ]\n")
        bw.write("  }" + (if (idx < results.size - 1) "," else "") + "\n")
      }
      bw.write("]\n")
    } finally bw.close()
  }

  def writeSummary(report: ComparisonReport, path: Path): Unit = {
    val total   = report.all.size
    val noDiff  = report.noDiff.size
    val hasDiff = report.hasDiff.size

    val fieldFreq = report.hasDiff
      .flatMap(_.fields.filter(!_.isSame).map(_.field))
      .groupBy(identity)
      .view.mapValues(_.size)
      .toList.sortBy(-_._2)

    val numericScores = report.hasDiff.map(_.totalDiffScore).filter(_ > 0)
    val avg = if (numericScores.nonEmpty) numericScores.sum / numericScores.size else 0.0
    val min = if (numericScores.nonEmpty) numericScores.min else 0.0
    val max = if (numericScores.nonEmpty) numericScores.max else 0.0

    val bw = new BufferedWriter(new FileWriter(path.toFile))
    try {
      bw.write("{\n")
      bw.write(s"""  "totalDocs": $total,\n""")
      bw.write(s"""  "noDiffCount": $noDiff,\n""")
      bw.write(s"""  "hasDiffCount": $hasDiff,\n""")
      bw.write(s"""  "onlyIn1Count": ${report.onlyIn1.size},\n""")
      bw.write(s"""  "onlyIn2Count": ${report.onlyIn2.size},\n""")
      bw.write(s"""  "numericDiff": { "avg": $avg, "min": $min, "max": $max },\n""")
      bw.write("""  "fieldFrequency": {""" + "\n")
      fieldFreq.zipWithIndex.foreach { case ((field, count), i) =>
        bw.write(s"""    ${jsonStr(field)}: $count""" + (if (i < fieldFreq.size - 1) "," else "") + "\n")
      }
      bw.write("  }\n")
      bw.write("}\n")
    } finally bw.close()
  }

  private def bsonJson(v: org.bson.BsonValue): String = v match {
    case s: org.bson.BsonString     => jsonStr(s.getValue)
    case i: org.bson.BsonInt32      => i.getValue.toString
    case l: org.bson.BsonInt64      => l.getValue.toString
    case d: org.bson.BsonDouble     => d.getValue.toString
    case b: org.bson.BsonBoolean    => b.getValue.toString
    case _: org.bson.BsonNull       => "null"
    case other                      => jsonStr(other.toString)
  }

  private def jsonStr(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
