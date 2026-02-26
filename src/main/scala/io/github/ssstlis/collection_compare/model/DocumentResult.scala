package io.github.ssstlis.collection_compare.model

import org.bson.BsonValue

case class DocumentResult(id: BsonValue, fields: List[FieldResult], hasDifferences: Boolean, totalDiffScore: Double)
