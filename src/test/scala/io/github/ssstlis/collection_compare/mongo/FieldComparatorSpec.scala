package io.github.ssstlis.collection_compare.mongo

import org.bson._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FieldComparatorSpec extends AnyFreeSpec with Matchers {

  "FieldComparator" - {

    "roundBson" - {

      "rounds BsonDouble up to zero decimal places" in {
        val result = FieldComparator.roundBson(new BsonDouble(3.7), 0)
        result shouldBe new BsonDouble(4.0)
      }

      "rounds BsonDouble to two decimal places" in {
        val result = FieldComparator.roundBson(new BsonDouble(1.2349), 2)
        result shouldBe new BsonDouble(1.23)
      }

      "returns BsonInt32 unchanged regardless of precision" in {
        val v = new BsonInt32(42)
        FieldComparator.roundBson(v, 3) should be theSameInstanceAs v
      }

      "returns BsonInt64 unchanged regardless of precision" in {
        val v = new BsonInt64(999L)
        FieldComparator.roundBson(v, 2) should be theSameInstanceAs v
      }

      "rounds BsonDecimal128 to the given precision" in {
        val v      = new BsonDecimal128(new org.bson.types.Decimal128(new java.math.BigDecimal("2.555")))
        val result = FieldComparator.roundBson(v, 2)
        result shouldBe new BsonDouble(2.56)
      }

      "returns non-numeric BsonValue unchanged" in {
        val v = new BsonString("hello")
        FieldComparator.roundBson(v, 2) should be theSameInstanceAs v
      }
    }

    "compareValues" - {

      "returns true when both values are None" in {
        FieldComparator.compareValues(None, None) shouldBe true
      }

      "returns true when both values are numerically equal" in {
        FieldComparator.compareValues(Some(new BsonInt32(5)), Some(new BsonInt32(5))) shouldBe true
      }

      "returns false when values differ" in {
        FieldComparator.compareValues(Some(new BsonInt32(1)), Some(new BsonInt32(2))) shouldBe false
      }

      "returns false when one side is absent" in {
        FieldComparator.compareValues(None, Some(new BsonInt32(1))) shouldBe false
      }

      "returns true when both string values are equal" in {
        val s = new BsonString("abc")
        FieldComparator.compareValues(Some(s), Some(new BsonString("abc"))) shouldBe true
      }
    }

    "numericDiff" - {

      "computes absolute difference for two doubles (v2 > v1)" in {
        FieldComparator.numericDiff(Some(new BsonDouble(100.0)), Some(new BsonDouble(160.0)), 0) shouldBe 60.0
      }

      "result is non-negative even when v1 > v2" in {
        FieldComparator.numericDiff(Some(new BsonDouble(200.0)), Some(new BsonDouble(150.0)), 0) shouldBe 50.0
      }

      "computes difference between BsonInt32 and BsonDouble" in {
        FieldComparator.numericDiff(Some(new BsonInt32(10)), Some(new BsonDouble(13.0)), 0) shouldBe 3.0
      }

      "returns 0.0 when one value is a non-numeric BsonString" in {
        FieldComparator.numericDiff(Some(new BsonString("x")), Some(new BsonDouble(5.0)), 0) shouldBe 0.0
      }

      "returns 0.0 when both values are None" in {
        FieldComparator.numericDiff(None, None, 0) shouldBe 0.0
      }

      "rounds the result to the given precision" in {
        // abs(1.456 - 0.0) = 1.456 → rounded to 2 dp = 1.46
        FieldComparator.numericDiff(Some(new BsonDouble(0.0)), Some(new BsonDouble(1.456)), 2) shouldBe 1.46
      }

      "returns 0.0 for equal values" in {
        FieldComparator.numericDiff(Some(new BsonInt64(42L)), Some(new BsonInt64(42L)), 0) shouldBe 0.0
      }
    }
  }
}
