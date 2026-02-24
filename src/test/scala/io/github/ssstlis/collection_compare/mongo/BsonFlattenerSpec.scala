package io.github.ssstlis.collection_compare.mongo

import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonString}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class BsonFlattenerSpec extends AnyFreeSpec with Matchers {

  "BsonFlattener" - {
    "flatten" - {

      "returns empty map for an empty document" in {
        BsonFlattener.flatten(new BsonDocument(), Set.empty) shouldBe empty
      }

      "returns a flat map for a simple document" in {
        val doc    = BsonDocument.parse("""{"name": "Alice", "age": 30}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result("name") shouldBe new BsonString("Alice")
        result("age") shouldBe new BsonInt32(30)
        result should have size 2
      }

      "always excludes _id at root level" in {
        val doc    = BsonDocument.parse("""{"_id": "root", "x": 1}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result.keys should not contain "_id"
        result should have size 1
      }

      "includes _id that appears inside a nested document" in {
        val doc    = BsonDocument.parse("""{"outer": {"_id": "nested", "val": 2}}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result.keys should contain("outer._id")
      }

      "flattens a two-level nested document with dot notation" in {
        val doc    = BsonDocument.parse("""{"a": {"b": 42}}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result("a.b") shouldBe new BsonInt32(42)
        result should have size 1
      }

      "flattens three levels deep" in {
        val doc    = BsonDocument.parse("""{"x": {"y": {"z": 7}}}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result("x.y.z") shouldBe new BsonInt32(7)
        result should have size 1
      }

      "skips a field listed in excludeFields" in {
        val doc    = BsonDocument.parse("""{"keep": 1, "skip": 2}""")
        val result = BsonFlattener.flatten(doc, Set("skip"))
        result.keys should contain only "keep"
      }

      "skips the entire subtree when the top-level key is excluded" in {
        val doc    = BsonDocument.parse("""{"meta": {"a": 1, "b": {"c": 2}}, "price": 99}""")
        val result = BsonFlattener.flatten(doc, Set("meta"))
        result.keys should contain only "price"
      }

      "keeps array values as leaf nodes without expanding them" in {
        val doc    = BsonDocument.parse("""{"tags": [1, 2, 3]}""")
        val result = BsonFlattener.flatten(doc, Set.empty)
        result("tags") shouldBe a[BsonArray]
        result should have size 1
      }
    }
  }
}
