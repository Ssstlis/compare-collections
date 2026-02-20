package io.github.ssstlis.collection_compare.mongo

import org.bson._

import scala.math.{pow, round}

object FieldComparator {

  /** Rounds a numeric BsonValue to the given decimal precision. Non-numeric values are returned as-is. */
  def roundBson(v: BsonValue, precision: Int): BsonValue = v match {
    case d: BsonDouble =>
      val factor = pow(10, precision)
      new BsonDouble(round(d.getValue * factor) / factor)
    case i: BsonInt32  => i
    case l: BsonInt64  => l
    case dc: BsonDecimal128 =>
      val factor = pow(10, precision)
      new BsonDouble(round(dc.getValue.bigDecimalValue().doubleValue() * factor) / factor)
    case other => other
  }

  /** Compare two optional BsonValues for equality (after optional rounding). */
  def compareValues(v1: Option[BsonValue], v2: Option[BsonValue]): Boolean =
    (v1, v2) match {
      case (None, None)       => true
      case (Some(a), Some(b)) => a == b
      case _                  => false
    }

  /** Returns |X2 - X1| (rounded), or 0.0 if either value is non-numeric. */
  def numericDiff(v1: Option[BsonValue], v2: Option[BsonValue], precision: Int): Double = {
    def toDouble(v: BsonValue): Option[Double] = v match {
      case d: BsonDouble      => Some(d.getValue)
      case i: BsonInt32       => Some(i.getValue.toDouble)
      case l: BsonInt64       => Some(l.getValue.toDouble)
      case dc: BsonDecimal128 => Some(dc.getValue.bigDecimalValue().doubleValue())
      case _                  => None
    }

    (v1.flatMap(toDouble), v2.flatMap(toDouble)) match {
      case (Some(a), Some(b)) =>
        val raw    = math.abs(b - a)
        val factor = pow(10, precision)
        round(raw * factor) / factor
      case _ => 0.0
    }
  }
}
