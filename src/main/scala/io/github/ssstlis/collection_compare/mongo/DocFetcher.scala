package io.github.ssstlis.collection_compare.mongo

import org.bson.BsonDocument
import org.mongodb.scala.Document

trait DocFetcher {
  def fetchDocs(collectionName: String, filter: BsonDocument, projectionExclude: List[String] = Nil): Seq[Document]

  def close(): Unit
}
