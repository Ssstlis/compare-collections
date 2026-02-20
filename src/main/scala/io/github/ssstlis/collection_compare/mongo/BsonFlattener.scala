package io.github.ssstlis.collection_compare.mongo

import org.bson.{BsonDocument, BsonValue}

import scala.jdk.CollectionConverters._

object BsonFlattener {

  /** Flattens a BsonDocument into a Map[String, BsonValue] using dot notation.
    * The `_id` field is always excluded from the returned map.
    * Fields listed in `excludeFields` are skipped.
    */
  def flatten(doc: BsonDocument, excludeFields: Set[String]): Map[String, BsonValue] = {
    val result = collection.mutable.Map.empty[String, BsonValue]
    flattenInto(doc, prefix = "", excludeFields, result)
    result.toMap
  }

  private def flattenInto(
    doc:           BsonDocument,
    prefix:        String,
    excludeFields: Set[String],
    result:        collection.mutable.Map[String, BsonValue]
  ): Unit =
    doc.entrySet().asScala.foreach { entry =>
      val key      = entry.getKey
      val fullKey  = if (prefix.isEmpty) key else s"$prefix.$key"
      val topLevel = if (prefix.isEmpty) key else prefix.takeWhile(_ != '.')

      // Always skip _id and excluded fields (matched at any nesting level by top-level key)
      if (key == "_id" && prefix.isEmpty) ()
      else if (excludeFields.contains(topLevel)) ()
      else {
        val value = entry.getValue
        if (value.isDocument) {
          flattenInto(value.asDocument(), fullKey, excludeFields, result)
        } else {
          result(fullKey) = value
        }
      }
    }
}
