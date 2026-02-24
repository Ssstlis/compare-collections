package io.github.ssstlis.collection_compare.mongo

import scala.jdk.CollectionConverters.ListHasAsScala

object BsonUtils {
  def bsonToString(v: org.bson.BsonValue): String = {
    import org.bson.BsonType
    if (v == null || v.getBsonType == BsonType.NULL) return ""
    v.getBsonType match {
      case BsonType.STRING     => v.asString().getValue
      case BsonType.INT32      => v.asInt32().getValue.toString
      case BsonType.INT64      => v.asInt64().getValue.toString
      case BsonType.DOUBLE     => v.asDouble().getValue.toString
      case BsonType.BOOLEAN    => v.asBoolean().getValue.toString
      case BsonType.DECIMAL128 => v.asDecimal128().getValue.toString
      case BsonType.OBJECT_ID  => v.asObjectId().getValue.toHexString
      case BsonType.DATE_TIME  =>
        val instant = java.time.Instant.ofEpochMilli(v.asDateTime().getValue)
        instant.atZone(java.time.ZoneOffset.UTC).toLocalDate.toString
      case BsonType.ARRAY =>
        val elems = v.asArray().getValues.asScala.map(bsonToString).mkString(", ")
        s"[$elems]"
      case _ => v.toString
    }
  }
}
