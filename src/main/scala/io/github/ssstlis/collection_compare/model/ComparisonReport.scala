package io.github.ssstlis.collection_compare.model

case class ComparisonReport(
  all: List[DocumentResult],
  noDiff: List[DocumentResult],
  hasDiff: List[DocumentResult], // sorted by totalDiffScore desc, common keys only
  onlyIn1: List[DocumentResult],
  onlyIn2: List[DocumentResult]
)
