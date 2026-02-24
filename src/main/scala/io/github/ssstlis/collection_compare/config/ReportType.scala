package io.github.ssstlis.collection_compare.config

sealed trait ReportType extends Product with Serializable {
  def ext: String = this match {
    case ReportType.Csv   => "csv"
    case ReportType.Json  => "json"
    case ReportType.Excel => "xlsx"
  }
}

object ReportType {
  def parse(s: String): Option[ReportType] = {
    s match {
      case "csv"   => Some(Csv)
      case "json"  => Some(Json)
      case "excel" => Some(Excel)
      case _       => None
    }
  }
  case object Csv   extends ReportType
  case object Json  extends ReportType
  case object Excel extends ReportType
}
