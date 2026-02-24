package io.github.ssstlis.collection_compare.model

import org.bson.BsonValue

case class FieldResult(
  field: String,
  value1: Option[BsonValue],
  value2: Option[BsonValue],
  isSame: Boolean,
  numericDiff: Double // 0.0 if not numeric
)
