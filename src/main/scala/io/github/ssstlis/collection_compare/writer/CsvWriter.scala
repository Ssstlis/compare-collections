package io.github.ssstlis.collection_compare.writer

import io.github.ssstlis.collection_compare.model.DocumentResult
import io.github.ssstlis.collection_compare.mongo.BsonUtils

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Path

object CsvWriter {

  def write(results: List[DocumentResult], path: Path): Unit = {
    if (results.isEmpty) { new java.io.File(path.toString).createNewFile(); return }

    val allFields = results.flatMap(_.fields.map(_.field)).distinct.sorted

    // For each field: is it numeric in any document of this result set?
    val isNumeric: Map[String, Boolean] = allFields.map { f =>
      f -> results.exists(_.fields.find(_.field == f).exists(_.numericDiff != 0.0))
    }.toMap

    // Column order per field: _1, _2, [diff], is_same
    val header: List[String] =
      "_id" :: allFields.flatMap { f =>
        val base = List(s"${f}_1", s"${f}_2")
        val diff = if (isNumeric(f)) List(s"abs_${f}_diff") else Nil
        base ++ diff ++ List(s"is_${f}_same")
      }

    val bw = new BufferedWriter(new FileWriter(path.toFile))
    try {
      bw.write(header.map(csvEscape).mkString(","))
      bw.newLine()

      results.foreach { doc =>
        val fieldMap = doc.fields.map(fr => fr.field -> fr).toMap
        val row: List[String] =
          BsonUtils.bsonToString(doc.id) :: allFields.flatMap { f =>
            val fr   = fieldMap.get(f)
            val v1   = fr.flatMap(_.value1).map(BsonUtils.bsonToString).getOrElse("")
            val v2   = fr.flatMap(_.value2).map(BsonUtils.bsonToString).getOrElse("")
            val base = List(v1, v2)
            val diff = if (isNumeric(f)) List(fr.map(_.numericDiff.toString).getOrElse("0.0")) else Nil
            val same = fr.map(_.isSame.toString).getOrElse("true")
            base ++ diff ++ List(same)
          }
        bw.write(row.map(csvEscape).mkString(","))
        bw.newLine()
      }
    } finally bw.close()
  }

  private def csvEscape(s: String): String = {
    if (s.contains(',') || s.contains('"') || s.contains('\n')) {
      "\"" + s.replace("\"", "\"\"") + "\""
    } else {
      s
    }
  }
}
