package io.github.ssstlis.collection_compare.mongo

import org.bson.BsonDocument
import org.mongodb.scala.Document
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source
import scala.util.Using

/** Reads a JSON array of documents in MongoDB Relaxed Extended JSON v2 format from a local file.
  *
  * The `collectionName`, `filter` and `projectionExclude` parameters received by [[fetchDocs]]
  * are ignored — the file is read as-is and all documents are returned.
  */
class FileDocFetcher(filePath: String) extends DocFetcher {
  private val logger: Logger = LoggerFactory.getLogger("FileDocFetcher")

  def fetchDocs(
    collectionName:    String,
    filter:            BsonDocument,
    projectionExclude: List[String] = Nil
  ): Seq[Document] = {
    logger.info(s"Reading documents from file: $filePath")
    val content = Using(Source.fromFile(filePath))(_.mkString).get
    val wrapped = BsonDocument.parse(s"""{"__data__": ${content.trim}}""")
    val arr     = wrapped.getArray("__data__")
    val docs    = (0 until arr.size()).map(i => Document(arr.get(i).asDocument()))
    logger.info(s"Loaded ${docs.size} documents from $filePath")
    docs
  }

  def close(): Unit = ()
}