package io.github.ssstlis.collection_compare.writer

import io.github.ssstlis.collection_compare.config.ExcelFormulaSeparator
import io.github.ssstlis.collection_compare.model.DocumentResult
import io.github.ssstlis.collection_compare.mongo.BsonUtils
import org.apache.poi.ss.usermodel._
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.{XSSFCell, XSSFColor, XSSFWorkbook}

import java.io.FileOutputStream
import java.nio.file.Path

object ExcelWriter {

  def write(results: List[DocumentResult], path: Path): Unit = {
    val wb    = new XSSFWorkbook()
    val sheet = wb.createSheet("Results")

    val allFields = results.flatMap(_.fields.map(_.field)).distinct.sorted

    val isNumeric: Map[String, Boolean] = allFields.map { f =>
      f -> results.exists(_.fields.find(_.field == f).exists(_.numericDiff != 0.0))
    }.toMap

    // Column order per field: _1, _2, [diff], is_same
    val headers: List[String] =
      "_id" :: allFields.flatMap { f =>
        val base = List(s"${f}_1", s"${f}_2")
        val diff = if (isNumeric(f)) List(s"abs_${f}_diff") else Nil
        base ++ diff ++ List(s"is_${f}_same")
      }

    val headerRow = sheet.createRow(0)
    headers.zipWithIndex.foreach { case (h, i) => headerRow.createCell(i).setCellValue(h) }

    results.zipWithIndex.foreach { case (doc, rowIdx) =>
      val row      = sheet.createRow(rowIdx + 1)
      val fieldMap = doc.fields.map(fr => fr.field -> fr).toMap

      val values: List[String] =
        BsonUtils.bsonToString(doc.id) :: allFields.flatMap { f =>
          val fr   = fieldMap.get(f)
          val v1   = fr.flatMap(_.value1).map(BsonUtils.bsonToString).getOrElse("")
          val v2   = fr.flatMap(_.value2).map(BsonUtils.bsonToString).getOrElse("")
          val base = List(v1, v2)
          val diff = if (isNumeric(f)) List(fr.map(_.numericDiff.toString).getOrElse("0.0")) else Nil
          val same = fr.map(_.isSame.toString).getOrElse("true")
          base ++ diff ++ List(same)
        }

      values.zipWithIndex.foreach { case (v, i) =>
        val cell = row.createCell(i)
        cell.setCellValue(v)
      }
    }

    (0 until math.min(headers.size, 20)).foreach(sheet.autoSizeColumn)

    val fos = new FileOutputStream(path.toFile)
    try wb.write(fos)
    finally { fos.close(); wb.close() }
  }

  /** Writes a has-diff report with the enriched layout:
    *   - Sheet "diffs": data shifted to column C (A and B reserved for cpid/hyperlinks)
    *   - Sheet "pfee diffs": filtered/sorted view for pfee differences
    *   - Sheet "mfee diffs": filtered/sorted view for mfee differences
    *   - Sheet "other diffs": rows where both pfee and mfee are equal but something else differs
    *   - Sheet "Analysis": empty, to be filled in manually (column C = cpids for back-links)
    *
    * is_*_same columns are written as booleans; abs_*_diff columns as doubles so that
    * FILTER(…=FALSE/TRUE) and SORT work correctly in Google Sheets / Excel 365.
    */
  def writeHasDiff(results: List[DocumentResult], path: Path, delim: ExcelFormulaSeparator): Unit = {
    import delim.d
    val wb     = new XSSFWorkbook()
    val sheet  = wb.createSheet("diffs")
    val offset = 2 // data starts at column C (0-indexed col 2)

    val allFields    = results.flatMap(_.fields.map(_.field)).distinct.sorted
    val isNumericMap = allFields.map { f =>
      f -> results.exists(_.fields.find(_.field == f).exists(_.numericDiff != 0.0))
    }.toMap

    val dataHeaders: List[String] =
      "_id" :: allFields.flatMap { f =>
        val base = List(s"${f}_1", s"${f}_2")
        val diff = if (isNumericMap(f)) List(s"abs_${f}_diff") else Nil
        base ++ diff ++ List(s"is_${f}_same")
      }

    // Column-index sets for typed cell writing (relative to data headers, 0-based)
    val isBoolIdx = dataHeaders.zipWithIndex
      .collect { case (h, i) if h.startsWith("is_") && h.endsWith("_same") => i }.toSet
    val isNumIdx  = dataHeaders.zipWithIndex
      .collect { case (h, i) if h.startsWith("abs_") && h.endsWith("_diff") => i }.toSet

    // ── Row 1 (POI 0): B1 = "cpid", data headers at C1.. ──────────────────
    val headerRow = sheet.createRow(0)
    headerRow.createCell(1).setCellValue("cpid")
    dataHeaders.zipWithIndex.foreach { case (h, i) =>
      headerRow.createCell(i + offset).setCellValue(h)
    }

    // ── Row 2 (POI 1): A2 = Analysis link, B2 = cpid formula; first data row ──
    val row2 = sheet.createRow(1)

    // A2: hyperlink to the matching row in the Analysis sheet (by cpid).
    // BYROW handles array iteration natively; ARRAYFORMULA must NOT wrap BYROW —
    // they conflict in Google Sheets and produce a syntax error.
    setRawFormula(
      row2.createCell(0),
      s"""BYROW(B2:B${d}LAMBDA(val${d}IF(val=""${d}""${d}""" +
        s"""IFERROR(HYPERLINK("#Analysis!A"&MATCH(val${d}Analysis!C:C${d}0)${d}"→ "&val)${d}""))))"""
    )

    // B2: extract cpid = first 8 chars of _id (column C)
    setRawFormula(
      row2.createCell(1),
      s"""BYROW(C2:C${d}LAMBDA(val${d}IF(val=""${d}""${d}LEFT(val${d}8))))"""
    )

    // ── Data rows (first data row shares POI row 1 with the formula cells) ──
    results.zipWithIndex.foreach { case (doc, rowIdx) =>
      val row      = if (rowIdx == 0) row2 else sheet.createRow(rowIdx + 1)
      val fieldMap = doc.fields.map(fr => fr.field -> fr).toMap

      val values: List[String] =
        BsonUtils.bsonToString(doc.id) :: allFields.flatMap { f =>
          val fr   = fieldMap.get(f)
          val v1   = fr.flatMap(_.value1).map(BsonUtils.bsonToString).getOrElse("")
          val v2   = fr.flatMap(_.value2).map(BsonUtils.bsonToString).getOrElse("")
          val base = List(v1, v2)
          val diff = if (isNumericMap(f)) List(fr.map(_.numericDiff.toString).getOrElse("0.0")) else Nil
          val same = fr.map(_.isSame.toString).getOrElse("true")
          base ++ diff ++ List(same)
        }

      values.zipWithIndex.foreach { case (v, i) =>
        val cell = row.createCell(i + offset)
        if (isBoolIdx.contains(i))
          cell.setCellValue(v == "true")
        else if (isNumIdx.contains(i))
          cell.setCellValue(v.toDoubleOption.getOrElse(0.0))
        else
          cell.setCellValue(v)
      }
    }

    (0 until math.min(dataHeaders.size + offset, 20)).foreach(sheet.autoSizeColumn)

    // Last column letter (e.g. "BF" for 56 data cols with offset 2: col index 57)
    val lastColName = CellReference.convertNumToColString(offset + dataHeaders.size - 1)

    addDiffSheet(wb, "pfee diffs", "is_pfee_same", "abs_pfee_diff", filterFalse = true, lastColName, delim)
    addDiffSheet(wb, "mfee diffs", "is_mfee_same", "abs_mfee_diff", filterFalse = true, lastColName, delim)
    addOtherDiffsSheet(wb, lastColName, delim)
    wb.createSheet("Analysis")

    val fos = new FileOutputStream(path.toFile)
    try wb.write(fos)
    finally { fos.close(); wb.close() }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Adds a single-filter diff sheet (pfee diffs / mfee diffs).
    *
    * Layout:
    *   A2  – BYROW formula with hyperlinks back to the matching row in "diffs"
    *   B1  – LET formula that returns the filtered/sorted table (spills right & down)
    */
  private def addDiffSheet(
    wb:          XSSFWorkbook,
    name:        String,
    filterCol:   String,
    sortCol:     String,
    filterFalse: Boolean,
    lastColName: String,
    delim: ExcelFormulaSeparator
  ): Unit = {
    import delim.d
    val sheet = wb.createSheet(name)
    val row0  = sheet.createRow(0)
    val row1  = sheet.createRow(1)
    val fVal  = if (filterFalse) "FALSE" else "TRUE"

    // A2: hyperlink back to the source row in the diffs sheet
    setRawFormula(
      row1.createCell(0),
      s"""BYROW(B2:B${d}LAMBDA(val${d}IF(val=""${d}""${d}""" +
        s"""IFERROR(HYPERLINK("#diffs!A"&MATCH(val${d}diffs!C:C${d}0)${d}"↑ "&val)${d}""))))"""
    )

    // B1: main filter/sort/display formula
    // CHOOSECOLS(…,SEQUENCE(1,COLUMNS(header))) selects ALL columns so nothing is lost.
    // To narrow down later, replace SEQUENCE(1,COLUMNS(header)) with explicit column indices.
    val formula =
      s"""LET(
  content${d}diffs!C2:${lastColName}10000${d}
  header${d}diffs!C1:${lastColName}1${d}
  filterColumnName${d}"${filterCol}"${d}
  filterColumn${d}MATCH(filterColumnName,header,0)${d}
  sortColumnName${d}"${sortCol}"${d}
  sortColumn${d}MATCH(sortColumnName${d}header${d}0)${d}
  filtered${d}FILTER(content${d}INDEX(content${d}0${d}filterColumn)=${fVal})${d}
  sorted${d}SORT(filtered${d}sortColumn${d}FALSE)${d}
  dataWithHeader${d}{header;sorted}${d}
  CHOOSECOLS(dataWithHeader${d}SEQUENCE(1${d}COLUMNS(header))))"""

    setRawFormula(row0.createCell(1), formula)
  }

  /** Adds the "other diffs" sheet: rows where BOTH is_mfee_same=TRUE and is_pfee_same=TRUE
    * (i.e. differences in fields other than mfee and pfee).
    */
  private def addOtherDiffsSheet(wb: XSSFWorkbook, lastColName: String, delim: ExcelFormulaSeparator): Unit = {
    import delim.d
    val sheet = wb.createSheet("other diffs")
    val row0  = sheet.createRow(0)
    val row1  = sheet.createRow(1)

    setRawFormula(
      row1.createCell(0),
      s"""BYROW(B2:B${d}LAMBDA(val${d}IF(val=""${d}""${d}""" +
        s"""IFERROR(HYPERLINK("#diffs!A"&MATCH(val${d}diffs!C:C${d}0)${d}"↑ "&val)${d}""))))"""
    )

    val formula =
      s"""LET(
  content${d}diffs!C2:${lastColName}10000${d}
  header${d}diffs!C1:${lastColName}1${d}
  filterColumnName0${d}"is_mfee_same"${d}
  filterColumn0${d}MATCH(filterColumnName0${d}header${d}0)${d}
  filterColumnName1${d}"is_pfee_same"${d}
  filterColumn1${d}MATCH(filterColumnName1${d}header${d}0)${d}
  sortColumnName${d}"abs_mfee_diff"${d}
  sortColumn${d}MATCH(sortColumnName${d}header${d}0)${d}
  filtered${d}FILTER(content${d}(INDEX(content${d}0${d}filterColumn0)=TRUE)*(INDEX(content${d}0${d}filterColumn1)=TRUE))${d}
  sorted${d}SORT(filtered${d}sortColumn${d}FALSE)${d}
  dataWithHeader${d}{header;sorted}${d}
  CHOOSECOLS(dataWithHeader${d}SEQUENCE(1${d}COLUMNS(header))))"""

    setRawFormula(row0.createCell(1), formula)
  }

  /** Writes a formula string directly to the cell's underlying XML, bypassing POI's
    * formula parser. This is necessary for functions that POI does not yet support
    * (LET, LAMBDA, BYROW, FILTER, SORT, CHOOSECOLS, SEQUENCE, ARRAYFORMULA, …).
    */
  private def setRawFormula(cell: Cell, formula: String): Unit =
    cell.asInstanceOf[XSSFCell].getCTCell.addNewF().setStringValue(formula)
}