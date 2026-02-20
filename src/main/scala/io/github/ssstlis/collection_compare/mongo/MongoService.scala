package io.github.ssstlis.collection_compare.mongo

import io.github.ssstlis.collection_compare.util.DurationFormatter
import me.tongfei.progressbar.ProgressBar
import org.bson.BsonDocument
import org.mongodb.scala.model.Projections
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import java.time.{Duration => JDuration}

class MongoService(config: MongoConfig, db: String, requestTimeout: Duration) extends DocFetcher {
  private val logger: Logger = LoggerFactory.getLogger("MongoService")
  private val client:   MongoClient   = MongoClient(config.connectionString)
  private val database: MongoDatabase = client.getDatabase(db)

  def fetchDocs(
    collectionName:    String,
    filter:            BsonDocument,
    projectionExclude: List[String] = Nil
  ): Seq[Document] = {
    val startMillis = System.currentTimeMillis()
    val collection: MongoCollection[Document] = database.getCollection(collectionName)

    val size = Await.result(collection.countDocuments(filter).toFuture(), requestTimeout)
    logger.info(s"Going to load $size documents from $collectionName")

    if (size == 0) {
      Nil
    } else {
      val flag = Promise[Boolean]()
      val accumulator = List.newBuilder[Document]

      val find = collection.find(filter)
      val request =
        if (projectionExclude.nonEmpty) {
          find.projection(Projections.exclude(projectionExclude: _*))
        } else {
          find
        }

      val collectionNameForPB = if (collectionName.length > 14) {
        collectionName.take(5) + "..." + collectionName.takeRight(10)
      } else collectionName

      val pb = new ProgressBar(s"${config.host.take(8)}-$db-$collectionNameForPB", size)

      request.subscribe(
            doc => {
              accumulator += doc
              pb.step()
            },
            (e: Throwable) => {
              logger.error(s"There was an error: $e")
              flag.failure(e)
            },
            () => flag.success(true)
      )

      try {
        Await.result(flag.future, requestTimeout)
      } finally {
        pb.close()
      }
      val timeDuration = DurationFormatter.prettyPrint(JDuration.ofMillis(System.currentTimeMillis() - startMillis))
      logger.info(s"Loaded $size documents from $collectionName in $timeDuration")
      accumulator.result()
    }
  }

  def close(): Unit = client.close()
}
