package io.github.ssstlis.collection_compare.util

import java.time.Duration

object DurationFormatter {
  val smallFractionsCount: Int = 2
  case class DurationStructuredExtra(millis: Long, micros: Long, nanos: Long)

  case class DurationStructured(
    days: Long,
    hours: Long,
    minutes: Long,
    seconds: Long,
    extras: Option[DurationStructuredExtra]
  ) {
    def getParts: List[Long] = {
      val baseParts = List(days, hours, minutes, seconds)
      extras.map(e => baseParts ::: List(e.millis, e.micros, e.nanos)).getOrElse(baseParts)
    }
  }

  def getStructure(duration: Duration, extra: Boolean = false): DurationStructured = {
    val days    = duration.toDays
    val hours   = duration.toHours    % 24
    val minutes = duration.toMinutes  % 60
    val seconds = duration.getSeconds % 60

    val extras = if (extra) {
      // Get total nanoseconds in the sub-second part
      val totalNanos   = duration.getNano
      val millis: Long = totalNanos / 1000000
      val micros: Long = (totalNanos % 1000000) / 1000
      val nanos: Long  = totalNanos  % 1000
      Some(DurationStructuredExtra(millis, micros, nanos))
    } else None
    DurationStructured(days, hours, minutes, seconds, extras)
  }

  /** Pretty-prints a Java Duration in human-readable format.
    *
    * Examples:
    *   - Duration.ofSeconds(45) => "45 seconds"
    *   - Duration.ofMinutes(5) => "5 minutes"
    *   - Duration.ofHours(2).plusMinutes(30) => "2 hours, 30 minutes"
    *   - Duration.ofDays(1).plusHours(3).plusMinutes(15).plusSeconds(30) => "1 day, 3 hours, 15 minutes, 30 seconds"
    *   - Duration.ofMillis(1500) => "1 second, 500 milliseconds"
    * @param showSmallFractions
    *   Flag for display or not display microseconds and nanoseconds if they are smallest of all units (default: true)
    */
  def prettyPrint(duration: Duration, showSmallFractions: Boolean = true): String = {
    if (duration.isZero) return "0 secs"
    if (duration.isNegative) return s"${prettyPrint(duration.negated(), showSmallFractions)} ago"

    val structuredDur = getStructure(duration, extra = true)
    import structuredDur._

    val greatest :: tail = getParts
      .zip(Seq("day", "hour", "min", "sec", "milli", "micro", "nano"))
      .filter(_._1 > 0)

    (greatest :: (if (showSmallFractions) tail else tail.dropRight(smallFractionsCount)))
      .map { case (value, unit) =>
        val plural = if (value == 1) unit else s"${unit}s"
        s"$value $plural"
      }
      .mkString(", ")
  }

  /** Compact version that shows only the most significant units.
    *
    * @param maxUnits
    *   Maximum number of time units to display (default: 2)
    * @param showSmallFractions
    *   Flag for display or not display microseconds and nanoseconds if they are smallest of all units (default: true)
    */
  def prettyPrintCompact(duration: Duration, maxUnits: Int = 3, showSmallFractions: Boolean = true): String = {
    if (duration.isZero) return "0s"
    if (duration.isNegative) return s"-${prettyPrintCompact(duration.negated(), maxUnits, showSmallFractions)}"

    val structuredDur = getStructure(duration, extra = true)
    import structuredDur._

    val greatest :: tail = getParts
      .zip(Seq("d", "h", "m", "s", "ms", "μs", "ns"))
      .filter(_._1 > 0)

    (greatest :: (if (showSmallFractions) tail else tail.dropRight(smallFractionsCount)))
      .map { case (value, unit) => s"$value$unit" }
      .take(maxUnits)
      .mkString(" ")
  }

  /** ISO 8601-style format with colons (HH:MM:SS). Shows days if present.
    */
  def prettyPrintTime(duration: Duration): String = {
    if (duration.isZero) return "00:00:00"
    if (duration.isNegative) return s"-${prettyPrintTime(duration.negated())}"

    val structuredDur = getStructure(duration)
    import structuredDur._

    val timeStr = f"$hours%02d:$minutes%02d:$seconds%02d"

    if (days > 0) s"${days}d $timeStr" else timeStr
  }
}
