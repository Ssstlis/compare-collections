package io.github.ssstlis.collection_compare.config

import io.github.ssstlis.collection_compare.config.ExcelFormulaSeparator.{Comma, Semicolon}

sealed trait ExcelFormulaSeparator extends Product with Serializable {
  def d: Char = this match {
    case Comma => ','
    case Semicolon => ';'
  }
}

object ExcelFormulaSeparator {

  def parse(s: String): Option[ExcelFormulaSeparator] = {
    s match {
      case "comma"     => Some(Comma)
      case "semicolon" => Some(Semicolon)
      case _           => None
    }
  }
  case object Comma     extends ExcelFormulaSeparator
  case object Semicolon extends ExcelFormulaSeparator
}
