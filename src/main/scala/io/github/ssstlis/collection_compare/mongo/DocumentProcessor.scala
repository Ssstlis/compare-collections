package io.github.ssstlis.collection_compare.mongo

import io.github.ssstlis.collection_compare.config.{AppConfig, FileConfig, RemoteConfig, SortDirection, SortMetric, SortSpec}
import io.github.ssstlis.collection_compare.model.{ComparisonReport, DocumentResult, FieldResult}
import org.bson.{BsonDocument, BsonString, BsonValue}
import org.mongodb.scala.Document

class DocumentProcessor(mongo1: DocFetcher, mongo2: DocFetcher) {

  def compareCollections(cfg: AppConfig): ComparisonReport = {
    val (filter, projectionExclude) = cfg match {
      case r: RemoteConfig => (BsonDocument.parse(r.filter), r.projectionExclude)
      case _: FileConfig   => (new BsonDocument(), Nil)
    }
    val excludeSet = cfg.excludeFields.toSet
    val precision  = cfg.roundPrecision

    println(s"Fetching documents from '${cfg.collection1}'...")
    val docs1 = mongo1.fetchDocs(cfg.collection1, filter, projectionExclude)
    println(s"Fetching documents from '${cfg.collection2}'...")
    val docs2 = mongo2.fetchDocs(cfg.collection2, filter, projectionExclude)

    println(s"Found ${docs1.size} documents in '${cfg.collection1}'")
    println(s"Found ${docs2.size} documents in '${cfg.collection2}'")

    val map1 = indexByKey(docs1, cfg.key)
    val map2 = indexByKey(docs2, cfg.key)

    val commonKeys  = (map1.keySet & map2.keySet).toSeq
    val onlyIn1Keys = (map1.keySet -- map2.keySet).toSeq
    val onlyIn2Keys = (map2.keySet -- map1.keySet).toSeq
    val totalCount  = commonKeys.size + onlyIn1Keys.size + onlyIn2Keys.size

    println(s"Processing $totalCount unique documents (${commonKeys.size} common, ${onlyIn1Keys.size} only-in-1, ${onlyIn2Keys.size} only-in-2)...")
    if (onlyIn1Keys.nonEmpty) println(s"  Only in col-1: ${previewKeys(onlyIn1Keys)}")
    if (onlyIn2Keys.nonEmpty) println(s"  Only in col-2: ${previewKeys(onlyIn2Keys)}")

    val commonResults = commonKeys.zipWithIndex.map { case (key, idx) =>
      if ((idx + 1) % 100 == 0) println(s"  Processed ${idx + 1}/${commonKeys.size} common...")
      flattenAndCompare(key, map1.get(key), map2.get(key), excludeSet, precision)
    }.toList

    val onlyIn1 = onlyIn1Keys.map(key => flattenAndCompare(key, map1.get(key), None, excludeSet, precision)).toList
    val onlyIn2 = onlyIn2Keys.map(key => flattenAndCompare(key, None, map2.get(key), excludeSet, precision)).toList

    val (noDiff, hasDiff) = commonResults.partition(!_.hasDifferences)
    val sortedHasDiff     = sortResults(hasDiff, cfg.sortBy, defaultByScore = true)
    val sortedNoDiff      = sortResults(noDiff,  cfg.sortBy, defaultByScore = false)
    val sortedOnlyIn1     = sortResults(onlyIn1, cfg.sortBy, defaultByScore = false)
    val sortedOnlyIn2     = sortResults(onlyIn2, cfg.sortBy, defaultByScore = false)
    ComparisonReport(
      all     = sortedNoDiff ++ sortedHasDiff ++ sortedOnlyIn1 ++ sortedOnlyIn2,
      noDiff  = sortedNoDiff,
      hasDiff = sortedHasDiff,
      onlyIn1 = sortedOnlyIn1,
      onlyIn2 = sortedOnlyIn2
    )
  }

  /** Returns a copy of `results` with only the columns that have at least one difference kept,
    * plus any key fields (minus "_id" which is separate) and `excludeFromCut` fields.
    */
  def applyCut(
    results:        List[DocumentResult],
    key:            List[String],
    excludeFromCut: List[String]
  ): List[DocumentResult] = {
    val fieldsWithDiffs = results.flatMap(_.fields.filter(!_.isSame).map(_.field)).toSet
    val alwaysKeep      = (key.filterNot(_ == "_id").toSet) ++ excludeFromCut.toSet
    val keepFields      = fieldsWithDiffs ++ alwaysKeep
    results.map(doc => doc.copy(fields = doc.fields.filter(fr => keepFields.contains(fr.field))))
  }

  private def buildKey(doc: Document, keyFields: List[String]): String = {
    val bson = doc.toBsonDocument
    keyFields.map(f => Option(bson.get(f)).map(BsonUtils.bsonToString).getOrElse("null")).mkString("|")
  }

  private def indexByKey(docs: Seq[Document], keyFields: List[String]): Map[String, Document] =
    docs.map(doc => buildKey(doc, keyFields) -> doc).toMap

  private def flattenAndCompare(
    key:       String,
    doc1:      Option[Document],
    doc2:      Option[Document],
    exclude:   Set[String],
    precision: Int
  ): DocumentResult = {
    def toFlat(d: Option[Document]): Map[String, BsonValue] =
      d.map(doc => BsonFlattener.flatten(doc.toBsonDocument, exclude))
       .getOrElse(Map.empty)

    val flat1 = toFlat(doc1)
    val flat2 = toFlat(doc2)

    val allFields = (flat1.keySet ++ flat2.keySet).toList.sorted

    val fieldResults = allFields.map { field =>
      val v1   = flat1.get(field).map(FieldComparator.roundBson(_, precision))
      val v2   = flat2.get(field).map(FieldComparator.roundBson(_, precision))
      val same = FieldComparator.compareValues(v1, v2)
      val diff = FieldComparator.numericDiff(v1, v2, precision)   // signed X2 - X1
      FieldResult(field, v1, v2, same, diff)
    }

    val hasDiff    = fieldResults.exists(!_.isSame)
    val totalScore = fieldResults.map(fr => math.abs(fr.numericDiff)).sum

    val idBson: BsonValue = doc1.orElse(doc2)
      .flatMap(d => Option(d.toBsonDocument.get("_id")))
      .getOrElse(new BsonString(key))

    DocumentResult(idBson, fieldResults, hasDiff, totalScore)
  }

  private def previewKeys(keys: Seq[String]): String = {
    val preview = keys.take(10).mkString(", ")
    if (keys.size > 10) s"[$preview, ...] (${keys.size} total)" else s"[$preview]"
  }

  private def sortResults(
    docs:           List[DocumentResult],
    sortBy:         List[SortSpec],
    defaultByScore: Boolean
  ): List[DocumentResult] =
    if (sortBy.nonEmpty)
      docs.sortWith { (a, b) =>
        val cmp = sortBy.foldLeft(0) { (acc, spec) =>
          if (acc != 0) acc
          else {
            val raw = compareSortKeys(extractSortKey(a, spec), extractSortKey(b, spec))
            if (spec.direction == SortDirection.Desc) -raw else raw
          }
        }
        cmp < 0
      }
    else if (defaultByScore)
      docs.sortBy(-_.totalDiffScore)
    else
      docs

  private def extractSortKey(doc: DocumentResult, spec: SortSpec): String =
    doc.fields.find(_.field == spec.field).map { fr =>
      spec.metric match {
        case SortMetric.Diff =>
          (if (spec.absolute) math.abs(fr.numericDiff) else fr.numericDiff).toString
        case SortMetric.V1 => bsonToStr(fr.value1, spec.absolute)
        case SortMetric.V2 => bsonToStr(fr.value2, spec.absolute)
      }
    }.getOrElse("")

  private def bsonToStr(v: Option[BsonValue], absolute: Boolean): String = {
    val s = v.fold("")(BsonUtils.bsonToString)
    if (absolute) s.toDoubleOption.map(d => math.abs(d).toString).getOrElse(s) else s
  }

  // Numeric strings are compared numerically; everything else lexicographically.
  private def compareSortKeys(a: String, b: String): Int =
    (a.toDoubleOption, b.toDoubleOption) match {
      case (Some(x), Some(y)) => x.compareTo(y)
      case _                  => a.compareTo(b)
    }
}