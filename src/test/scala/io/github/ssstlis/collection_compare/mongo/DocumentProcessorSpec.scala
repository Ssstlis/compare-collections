package io.github.ssstlis.collection_compare.mongo

import io.github.ssstlis.collection_compare.config.{RemoteConfig, SortSpec}
import io.github.ssstlis.collection_compare.model.{DocumentResult, FieldResult}
import org.bson.{BsonDocument, BsonInt32, BsonString}
import org.mongodb.scala.Document
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DocumentProcessorSpec extends AnyFreeSpec with Matchers {

  // ── helpers ──────────────────────────────────────────────────────────────

  private def fakeService(docs: Seq[Document]): DocFetcher = new DocFetcher {
    def fetchDocs(name: String, filter: BsonDocument, proj: List[String]): Seq[Document] = docs
    def close(): Unit                                                                    = ()
  }

  private def doc(json: String): Document = Document(BsonDocument.parse(json))

  private def baseCfg(sortBy: List[SortSpec] = Nil): RemoteConfig =
    RemoteConfig(collection1 = "c1", collection2 = "c2", db1 = "db1", db2 = "db2", sortBy = sortBy)

  private def processor(col1: Seq[Document], col2: Seq[Document]): DocumentProcessor =
    new DocumentProcessor(fakeService(col1), fakeService(col2))

  "DocumentProcessor" - {
    // ── compareCollections ───────────────────────────────────────────────────

    "compareCollections" - {

      "places a matched document with no field differences into noDiff" in {
        val d      = doc("""{"_id": "a", "x": 1}""")
        val report = processor(Seq(d), Seq(d)).compareCollections(baseCfg())
        report.noDiff should have size 1
        report.hasDiff shouldBe empty
        report.onlyIn1 shouldBe empty
        report.onlyIn2 shouldBe empty
      }

      "places a matched document with field differences into hasDiff" in {
        val report = processor(Seq(doc("""{"_id": "a", "amount": 100}""")), Seq(doc("""{"_id": "a", "amount": 200}""")))
          .compareCollections(baseCfg())
        report.hasDiff should have size 1
        report.noDiff shouldBe empty
        report.hasDiff.head.totalDiffScore shouldBe 100.0
      }

      "places a document absent in col2 into onlyIn1" in {
        val common = doc("""{"_id": "shared", "v": 1}""")
        val extra  = doc("""{"_id": "extra1", "v": 5}""")
        val report = processor(Seq(common, extra), Seq(common)).compareCollections(baseCfg())
        report.onlyIn1 should have size 1
        report.onlyIn2 shouldBe empty
        report.noDiff should have size 1
      }

      "places a document absent in col1 into onlyIn2" in {
        val common = doc("""{"_id": "shared", "v": 1}""")
        val extra  = doc("""{"_id": "extra2", "v": 9}""")
        val report = processor(Seq(common), Seq(common, extra)).compareCollections(baseCfg())
        report.onlyIn2 should have size 1
        report.onlyIn1 shouldBe empty
      }

      "sorts hasDiff by totalDiffScore descending by default" in {
        // doc_a diff=5, doc_b diff=100 → doc_b first
        val report = processor(
          Seq(doc("""{"_id": "a", "x": 10}"""), doc("""{"_id": "b", "x": 100}""")),
          Seq(doc("""{"_id": "a", "x": 15}"""), doc("""{"_id": "b", "x": 200}"""))
        ).compareCollections(baseCfg())
        val scores = report.hasDiff.map(_.totalDiffScore)
        scores shouldBe List(100.0, 5.0)
      }

      "custom sortBy overrides the default totalDiffScore sort" in {
        // doc_a: amount_1=100, diff=100  → first by default (higher score)
        // doc_b: amount_1=300, diff=5    → first by amount_1 desc
        val report = processor(
          Seq(doc("""{"_id": "a", "amount": 100}"""), doc("""{"_id": "b", "amount": 300}""")),
          Seq(doc("""{"_id": "a", "amount": 200}"""), doc("""{"_id": "b", "amount": 305}"""))
        ).compareCollections(baseCfg(sortBy = SortSpec.parseAll("amount_1 desc")))

        val ids = report.hasDiff.map(d => d.id.toString)
        // doc_b (amount 300) should come before doc_a (amount 100)
        ids.head should include("b")
        ids.last should include("a")
      }
    }

    // ── applyCut ─────────────────────────────────────────────────────────────

    "applyCut" - {

      val proc = new DocumentProcessor(fakeService(Nil), fakeService(Nil))

      def fr(name: String, same: Boolean, diff: Double = 0.0): FieldResult =
        FieldResult(
          name,
          Some(new BsonInt32(1)),
          Some(new BsonInt32(if (same) 1 else 2)),
          isSame = same,
          numericDiff = diff
        )

      "removes fields that are identical in every document" in {
        val docR = DocumentResult(
          new BsonString("id"),
          List(fr("same_f", same = true), fr("diff_f", same = false, diff = 1.0)),
          hasDifferences = true,
          totalDiffScore = 1.0
        )

        val cut = proc.applyCut(List(docR), key = List("_id"), excludeFromCut = Nil)
        cut.head.fields.map(_.field) shouldBe List("diff_f")
      }

      "keeps a field when it differs in at least one document" in {
        val allSame =
          DocumentResult(new BsonString("i1"), List(fr("f", same = true)), hasDifferences = false, totalDiffScore = 0)
        val onesDiff = DocumentResult(
          new BsonString("i2"),
          List(fr("f", same = false, diff = 5.0)),
          hasDifferences = true,
          totalDiffScore = 5
        )

        val cut = proc.applyCut(List(allSame, onesDiff), key = Nil, excludeFromCut = Nil)
        cut.forall(_.fields.exists(_.field == "f")) shouldBe true
      }

      "always keeps non-_id key fields even when their values are identical" in {
        val docR = DocumentResult(
          new BsonString("id"),
          List(fr("region", same = true), fr("price", same = true)),
          hasDifferences = false,
          totalDiffScore = 0
        )

        val cut = proc.applyCut(List(docR), key = List("region"), excludeFromCut = Nil)
        cut.head.fields.map(_.field) shouldBe List("region")
      }

      "always keeps excludeFromCut fields even when their values are identical" in {
        val docR = DocumentResult(
          new BsonString("id"),
          List(fr("currency", same = true), fr("noise", same = true)),
          hasDifferences = false,
          totalDiffScore = 0
        )

        val cut = proc.applyCut(List(docR), key = Nil, excludeFromCut = List("currency"))
        cut.head.fields.map(_.field) shouldBe List("currency")
      }

      "returns an empty list for empty input" in {
        proc.applyCut(Nil, key = List("_id"), excludeFromCut = Nil) shouldBe empty
      }
    }
  }

}
