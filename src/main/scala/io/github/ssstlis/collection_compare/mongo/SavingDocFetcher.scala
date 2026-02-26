package io.github.ssstlis.collection_compare.mongo

import org.bson.BsonDocument
import org.bson.json.{JsonMode, JsonWriterSettings}
import org.mongodb.scala.Document
import org.slf4j.{Logger, LoggerFactory}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Wraps another [[DocFetcher]] and saves the fetched documents to `savePath` as a MongoDB Relaxed Extended JSON v2
  * array immediately after fetching.
  */
class SavingDocFetcher(underlying: DocFetcher, savePath: Path) extends DocFetcher {
  private val logger: Logger = LoggerFactory.getLogger("SavingDocFetcher")

  private val jsonSettings: JsonWriterSettings =
    JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build()

  def fetchDocs(collectionName: String, filter: BsonDocument, projectionExclude: List[String] = Nil): Seq[Document] = {
    val docs    = underlying.fetchDocs(collectionName, filter, projectionExclude)
    val content = docs.map(_.toBsonDocument.toJson(jsonSettings)).mkString("[\n", ",\n", "\n]")
    Files.write(savePath, content.getBytes(StandardCharsets.UTF_8))
    logger.info(s"Saved ${docs.size} documents to $savePath")
    println(s"  Saved ${docs.size} raw documents to $savePath")
    docs
  }

  def close(): Unit = underlying.close()
}
