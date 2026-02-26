package io.github.ssstlis.collection_compare.writer

import io.circe.{Json, Printer}
import io.circe.syntax._
import io.github.ssstlis.collection_compare.model.{DocumentResult, FieldResult}
import io.github.ssstlis.collection_compare.mongo.BsonUtils

import java.nio.file.{Files, Path}

object JsonWriter {

  def write(results: List[DocumentResult], path: Path): Unit = {
    val json = Json.arr(results.map(docJson): _*)
    Files.writeString(path, json.printWith(Printer.spaces2))
  }

  private def docJson(doc: DocumentResult): Json =
    Json.obj(
      "_id"            -> BsonUtils.bsonJson(doc.id),
      "totalDiffScore" -> doc.totalDiffScore.asJson,
      "hasDifferences" -> doc.hasDifferences.asJson,
      "fields"         -> Json.arr(doc.fields.map(fieldJson): _*)
    )

  private def fieldJson(fr: FieldResult): Json =
    Json.obj(
      "field"       -> fr.field.asJson,
      "value1"      -> fr.value1.fold(Json.Null)(BsonUtils.bsonJson),
      "value2"      -> fr.value2.fold(Json.Null)(BsonUtils.bsonJson),
      "isSame"      -> fr.isSame.asJson,
      "numericDiff" -> fr.numericDiff.asJson
    )
}
