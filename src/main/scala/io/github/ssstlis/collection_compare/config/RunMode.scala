package io.github.ssstlis.collection_compare.config

sealed trait RunMode
object RunMode {
  case object Remote extends RunMode { override def toString = "remote" }
  case object File   extends RunMode { override def toString = "file"   }

  def parse(s: String): Either[String, RunMode] = s.toLowerCase match {
    case "remote" => Right(Remote)
    case "file"   => Right(File)
    case other    => Left(s"Unknown mode '$other'. Expected: remote or file")
  }
}
