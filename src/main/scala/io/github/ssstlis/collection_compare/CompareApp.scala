package io.github.ssstlis.collection_compare

import io.github.ssstlis.collection_compare.config.{CliParser, FileConfig, RemoteConfig}
import io.github.ssstlis.collection_compare.mongo._

object CompareApp extends App {
  CliParser.parse(args) match {
    case None =>
      sys.exit(1)

    case Some(cfg) =>
      val outDir = ReportOrchestrator.makeDir(cfg.outputPath)

      val (fetcher1, fetcher2): (DocFetcher, DocFetcher) = cfg match {

        case r: RemoteConfig =>
          println(s"""|=== MongoDB Collection Comparison Tool ===
                      |  Mode               : remote
                      |  Config key 1       : ${r.host1}
                      |  Config key 2       : ${r.host2}
                      |  DB 1               : ${r.db1}
                      |  DB 2               : ${r.db2}
                      |  Collection 1       : ${r.collection1}
                      |  Collection 2       : ${r.collection2}
                      |  Keep raw docs      : ${r.keep}
                      |  Filter             : ${r.filter}
                      |  Request timeout    : ${r.requestTimeout}
                      |  Exclude fields     : ${if (r.excludeFields.isEmpty) "-" else r.excludeFields.mkString(", ")}
                      |  Projection excl.   : ${if (r.projectionExclude.isEmpty) "-" else r.projectionExclude.mkString(", ")}
                      |  Round precision    : ${r.roundPrecision}
                      |  Key                : ${r.key.mkString(", ")}
                      |  Exclude from cut   : ${if (r.excludeFromCut.isEmpty) "-" else r.excludeFromCut.mkString(", ")}
                      |  Sort               : ${if (r.sortBy.isEmpty) "-" else r.sortBy.map(_.asInfoString).mkString(", ")}
                      |  Output path        : ${r.outputPath}
                      |  Reports            : ${r.reports.mkString(", ")}
                      |  Formulas delimiter : ${r.excelFormulasDelimiter}
                      |""".stripMargin('|'))

          val mongo1 = new MongoService(MongoConfig.load(r.host1), r.db1, r.requestTimeout)
          val mongo2 = new MongoService(MongoConfig.load(r.host2), r.db2, r.requestTimeout)

          if (r.keep) {
            (
              new SavingDocFetcher(mongo1, outDir.resolve(s"${r.host1}-${r.db1}-${r.collection1}.json")),
              new SavingDocFetcher(mongo2, outDir.resolve(s"${r.host2}-${r.db2}-${r.collection2}.json"))
            )
          } else {
            (mongo1, mongo2)
          }

        case f: FileConfig =>
          println(s"""|=== MongoDB Collection Comparison Tool ===
                      |  Mode               : file
                      |  File 1             : ${f.file1}
                      |  File 2             : ${f.file2}
                      |  Collection 1       : ${f.collection1}
                      |  Collection 2       : ${f.collection2}
                      |  Exclude fields     : ${if (f.excludeFields.isEmpty) "-" else f.excludeFields.mkString(", ")}
                      |  Round precision    : ${f.roundPrecision}
                      |  Key                : ${f.key.mkString(", ")}
                      |  Exclude from cut   : ${if (f.excludeFromCut.isEmpty) "-" else f.excludeFromCut.mkString(", ")}
                      |  Sort               : ${if (f.sortBy.isEmpty) "-" else f.sortBy.map(_.asInfoString).mkString(", ")}
                      |  Output path        : ${f.outputPath}
                      |  Reports            : ${f.reports.mkString(", ")}
                      |  Formulas delimiter : ${f.excelFormulasDelimiter}
                      |""".stripMargin('|'))

          (new FileDocFetcher(f.file1), new FileDocFetcher(f.file2))
      }

      val processor = new DocumentProcessor(fetcher1, fetcher2)

      val report =
        try processor.compareCollections(cfg)
        finally { fetcher1.close(); fetcher2.close() }

      val hasDiffCut = processor.applyCut(report.hasDiff, cfg.key, cfg.excludeFromCut)

      val (host1Label, host2Label) = cfg match {
        case r: RemoteConfig => (r.host1, r.host2)
        case _: FileConfig   => ("file", "file")
      }

      //format:off
      ReportOrchestrator.write(
        report, hasDiffCut, outDir, cfg.reports,
        host1Label, cfg.collection1,
        host2Label, cfg.collection2,
        cfg.excelFormulasDelimiter
      )
      //format:on

      println()
      println("=== COMPARISON COMPLETE ===")
      println(s"  Total documents    : ${report.all.size}")
      println(s"  No differences     : ${report.noDiff.size}")
      println(s"  With differences   : ${report.hasDiff.size}")
      println(s"  Only in col-1      : ${report.onlyIn1.size}")
      println(s"  Only in col-2      : ${report.onlyIn2.size}")
      println(s"  Cut columns kept   : ${hasDiffCut.headOption.map(_.fields.size).getOrElse(0)} / ${report.hasDiff.headOption.map(_.fields.size).getOrElse(0)}")
      println(s"  Reports written to : $outDir")
  }
}