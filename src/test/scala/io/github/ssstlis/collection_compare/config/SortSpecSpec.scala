package io.github.ssstlis.collection_compare.config

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SortSpecSpec extends AnyFreeSpec with Matchers {

  "SortSpec" - {

    "parse" - {

      "parses abs_ prefix with _diff metric and explicit desc direction" in {
        SortSpec.parse("abs_pnl_diff desc") shouldBe
          Right(SortSpec("pnl", SortMetric.Diff, SortDirection.Desc, absolute = true))
      }

      "parses _1 metric with asc direction" in {
        SortSpec.parse("amount_1 asc") shouldBe
          Right(SortSpec("amount", SortMetric.V1, SortDirection.Asc, absolute = false))
      }

      "parses _2 metric and defaults to desc when direction is omitted" in {
        SortSpec.parse("fee_2") shouldBe
          Right(SortSpec("fee", SortMetric.V2, SortDirection.Desc, absolute = false))
      }

      "parses field name that itself contains underscores" in {
        SortSpec.parse("my_long_field_diff desc") shouldBe
          Right(SortSpec("my_long_field", SortMetric.Diff, SortDirection.Desc, absolute = false))
      }

      "prefers _diff suffix over _1/_2 when the name ends with _diff" in {
        // field "score_diff" with metric _1  — last suffix wins
        SortSpec.parse("score_diff_1 asc") shouldBe
          Right(SortSpec("score_diff", SortMetric.V1, SortDirection.Asc, absolute = false))
      }

      "accepts uppercase direction token" in {
        SortSpec.parse("amount_1 ASC") shouldBe
          Right(SortSpec("amount", SortMetric.V1, SortDirection.Asc, absolute = false))
      }

      "treats unrecognised direction token as part of the body and defaults to desc" in {
        // "pnl_diff foo" — "foo" is not asc/desc, so whole string is the body,
        // which no longer ends with a known suffix → Left
        SortSpec.parse("pnl_diff foo") shouldBe a[Left[_, _]]
      }

      "returns Left for a spec with no recognised suffix" in {
        SortSpec.parse("fieldname_unknown") shouldBe a[Left[_, _]]
      }

      "returns Left for an empty string" in {
        SortSpec.parse("") shouldBe a[Left[_, _]]
      }
    }

    "parseAll" - {

      "parses a comma-separated list of valid specs" in {
        val specs = SortSpec.parseAll("abs_pnl_diff desc, amount_1 asc")
        specs should have size 2
        specs(0) shouldBe SortSpec("pnl", SortMetric.Diff, SortDirection.Desc, absolute = true)
        specs(1) shouldBe SortSpec("amount", SortMetric.V1, SortDirection.Asc, absolute = false)
      }

      "silently skips invalid specs and returns only the valid ones" in {
        val specs = SortSpec.parseAll("abs_pnl_diff desc, INVALID, fee_2 asc")
        specs should have size 2
        specs.map(_.field) shouldBe List("pnl", "fee")
      }

      "returns an empty list for an empty input string" in {
        SortSpec.parseAll("") shouldBe empty
      }

      "ignores blank tokens produced by extra commas" in {
        SortSpec.parseAll(" , amount_1 asc , ") should have size 1
      }
    }
  }
}
