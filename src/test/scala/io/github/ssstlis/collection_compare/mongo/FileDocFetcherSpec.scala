package io.github.ssstlis.collection_compare.mongo

import org.bson.BsonDocument
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class FileDocFetcherSpec extends AnyFreeSpec with Matchers {

  private def withTempFile(content: String)(f: String => Unit): Unit = {
    val file = Files.createTempFile("filedocfetcher-test", ".json")
    try {
      Files.write(file, content.getBytes(StandardCharsets.UTF_8))
      f(file.toString)
    } finally {
      Files.deleteIfExists(file)
    }
  }

  "FileDocFetcher" - {

    "reads all documents from a JSON array file" in {
      val json =
        """|[
           |  {"_id": 1, "name": "Alice", "amount": 100.0},
           |  {"_id": 2, "name": "Bob",   "amount": 200.5}
           |]""".stripMargin

      withTempFile(json) { path =>
        val docs = new FileDocFetcher(path).fetchDocs("ignored", BsonDocument.parse("{}"))
        docs should have size 2
        docs(0).toBsonDocument.getInt32("_id").getValue   shouldBe 1
        docs(1).toBsonDocument.getInt32("_id").getValue   shouldBe 2
        docs(0).toBsonDocument.getString("name").getValue shouldBe "Alice"
        docs(1).toBsonDocument.getString("name").getValue shouldBe "Bob"
        docs(0).toBsonDocument.getDouble("amount").getValue shouldBe 100.0
        docs(1).toBsonDocument.getDouble("amount").getValue shouldBe 200.5
      }
    }

    "returns an empty sequence for an empty JSON array" in {
      withTempFile("[]") { path =>
        val docs = new FileDocFetcher(path).fetchDocs("col", BsonDocument.parse("{}"))
        docs shouldBe empty
      }
    }

    "ignores collectionName and filter parameters — returns all documents regardless" in {
      val json = """[{"_id": 1, "x": 10}, {"_id": 2, "x": 5}]"""

      withTempFile(json) { path =>
        val restrictiveFilter = BsonDocument.parse("""{"x": {"$gt": 100}}""")
        val docs = new FileDocFetcher(path).fetchDocs("some_other_collection", restrictiveFilter)
        // filter is ignored; both documents are returned
        docs should have size 2
      }
    }

    "reads Extended JSON ObjectId values correctly" in {
      val json =
        """|[
           |  {"_id": {"$oid": "507f1f77bcf86cd799439011"}, "value": 42}
           |]""".stripMargin

      withTempFile(json) { path =>
        val docs = new FileDocFetcher(path).fetchDocs("col", BsonDocument.parse("{}"))
        docs should have size 1
        docs(0).toBsonDocument.isObjectId("_id") shouldBe true
        docs(0).toBsonDocument.getObjectId("_id").getValue.toString shouldBe "507f1f77bcf86cd799439011"
        docs(0).toBsonDocument.getInt32("value").getValue shouldBe 42
      }
    }

    "reads Extended JSON DateTime values correctly" in {
      val json =
        """|[
           |  {"_id": 1, "ts": {"$date": {"$numberLong": "1640000000000"}}}
           |]""".stripMargin

      withTempFile(json) { path =>
        val docs = new FileDocFetcher(path).fetchDocs("col", BsonDocument.parse("{}"))
        docs should have size 1
        docs(0).toBsonDocument.isDateTime("ts") shouldBe true
        docs(0).toBsonDocument.getDateTime("ts").getValue shouldBe 1640000000000L
      }
    }

    "reads Extended JSON Decimal128 values correctly" in {
      val json =
        """|[
           |  {"_id": 1, "score": {"$numberDecimal": "3.14159"}}
           |]""".stripMargin

      withTempFile(json) { path =>
        val docs = new FileDocFetcher(path).fetchDocs("col", BsonDocument.parse("{}"))
        docs should have size 1
        docs(0).toBsonDocument.isDecimal128("score") shouldBe true
        docs(0).toBsonDocument.getDecimal128("score").getValue.toString shouldBe "3.14159"
      }
    }

    "reads nested documents and arrays correctly" in {
      val json =
        """|[
           |  {"_id": 1, "meta": {"region": "EU", "tags": ["a", "b"]}}
           |]""".stripMargin

      withTempFile(json) { path =>
        val docs = new FileDocFetcher(path).fetchDocs("col", BsonDocument.parse("{}"))
        docs should have size 1
        val meta = docs(0).toBsonDocument.getDocument("meta")
        meta.getString("region").getValue shouldBe "EU"
        meta.getArray("tags").get(0).asString().getValue shouldBe "a"
      }
    }

    "close() does not throw" in {
      withTempFile("[{\"_id\": 1}]") { path =>
        noException should be thrownBy new FileDocFetcher(path).close()
      }
    }
  }
}