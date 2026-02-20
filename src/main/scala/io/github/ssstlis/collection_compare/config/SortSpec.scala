package io.github.ssstlis.collection_compare.config

sealed trait SortDirection
object SortDirection {
  case object Asc  extends SortDirection
  case object Desc extends SortDirection
}

sealed trait SortMetric
object SortMetric {
  case object Diff extends SortMetric  // numericDiff (v2 − v1)
  case object V1   extends SortMetric  // numeric value from collection 1
  case object V2   extends SortMetric  // numeric value from collection 2
}

/** One sort criterion.
  *
  * Format: `[abs_]<fieldName>_(diff|1|2) [asc|desc]`
  *   - `abs_`  prefix  — sort by absolute value
  *   - `_diff` suffix  — sort by `numericDiff` (v2 − v1)
  *   - `_1`    suffix  — sort by numeric value from collection 1
  *   - `_2`    suffix  — sort by numeric value from collection 2
  *   - direction defaults to `desc` when omitted
  *
  * Examples:
  *   `abs_pnl_diff desc`   → |numericDiff| of field "pnl", descending
  *   `amount_1 asc`        →  value1 of field "amount", ascending
  *   `fee_diff`            →  numericDiff of field "fee", descending (default)
  */
case class SortSpec(
  field:     String,
  metric:    SortMetric,
  direction: SortDirection,
  absolute:  Boolean
) {
  def asInfoString = s"$field $direction"
}

object SortSpec {

  def parse(raw: String): Either[String, SortSpec] = {
    val s = raw.trim

    // 1. Split optional trailing direction token
    val (body, direction) = {
      val i = s.lastIndexOf(' ')
      if (i < 0) (s, SortDirection.Desc)
      else s.substring(i + 1).toLowerCase match {
        case "asc"  => (s.substring(0, i).trim, SortDirection.Asc)
        case "desc" => (s.substring(0, i).trim, SortDirection.Desc)
        case _      => (s, SortDirection.Desc)
      }
    }

    // 2. Strip optional leading `abs_`
    val (isAbs, withoutAbs) =
      if (body.startsWith("abs_")) (true, body.drop(4))
      else (false, body)

    // 3. Detect metric from suffix (_diff checked before _1/_2 to avoid false match on e.g. "fee_diff_1")
    val parsed: Option[(String, SortMetric)] =
      if (withoutAbs.endsWith("_diff") && withoutAbs.length > 5)
        Some(withoutAbs.dropRight(5) -> SortMetric.Diff)
      else if (withoutAbs.endsWith("_1") && withoutAbs.length > 2)
        Some(withoutAbs.dropRight(2) -> SortMetric.V1)
      else if (withoutAbs.endsWith("_2") && withoutAbs.length > 2)
        Some(withoutAbs.dropRight(2) -> SortMetric.V2)
      else None

    parsed match {
      case Some((field, metric)) => Right(SortSpec(field, metric, direction, isAbs))
      case None =>
        Left(s"Cannot parse sort spec '$raw'. Expected: [abs_]<field>_(diff|1|2) [asc|desc]")
    }
  }

  def parseAll(s: String): List[SortSpec] =
    s.split(',').map(_.trim).filter(_.nonEmpty).flatMap { token =>
      parse(token) match {
        case Right(spec) => Some(spec)
        case Left(err)   => { System.err.println(s"[warn] $err"); None }
      }
    }.toList
}