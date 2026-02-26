package io.github.ssstlis.collection_compare.writer

import io.github.ssstlis.collection_compare.model.DocumentResult
import io.circe.{Json, Printer}
import io.circe.syntax._
import io.github.ssstlis.collection_compare.model.{DocumentResult, FieldResult}
import org.bson._

import java.nio.file.{Files, Path}

object RawJsonWriter {
  def write(
    diffs: List[DocumentResult],
    onlyIn1: List[DocumentResult],
    onlyIn2: List[DocumentResult],
    path: Path
  ): Unit = {
    Json.obj(
      "only_in_file1" -> Json.arr(),
      "only_in_file2" -> Json.arr(),
      "different_values" -> Json.arr(),
    )
  }
}
