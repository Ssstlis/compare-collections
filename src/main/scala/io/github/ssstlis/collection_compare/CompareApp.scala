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

        case r: RemoteConfig => {
          if (r.excludeFields.nonEmpty) println(r.excludeFields)

          val mongo1 = new MongoService(MongoConfig.load(r.host1), r.db1, r.requestTimeout)
          val mongo2 = new MongoService(MongoConfig.load(r.host2), r.db2, r.requestTimeout)

          if (r.keep) {
            val path1 = outDir.resolve(s"${r.host1}-${r.db1}-${r.collection1}.json")
            val path2 = outDir.resolve(s"${r.host2}-${r.db2}-${r.collection2}.json")
            (new SavingDocFetcher(mongo1, path1), new SavingDocFetcher(mongo2, path2))
          } else {
            (mongo1, mongo2)
          }
        }
        case f: FileConfig => {
          println(f.configExplain)
          (new FileDocFetcher(f.file1), new FileDocFetcher(f.file2))
        }
      }

      val processor = new DocumentProcessor(fetcher1, fetcher2)

      val report = try {
        processor.compareCollections(cfg)
      } finally {
        fetcher1.close();
        fetcher2.close()
      }

      val hasDiffCut = processor.applyCut(report.hasDiff, cfg.key, cfg.excludeFromCut)

      val (host1Label, host2Label) = cfg match {
        case r: RemoteConfig => (r.host1, r.host2)
        case _: FileConfig   => ("file", "file")
      }

      // format:off
      ReportOrchestrator.write(
        report,
        hasDiffCut,
        outDir,
        cfg.reports,
        host1Label,
        cfg.collection1,
        host2Label,
        cfg.collection2,
        cfg.excelFormulasDelimiter
      )
      // format:on

      println()
      println(s"""=== COMPARISON COMPLETE ===
                 |  Total documents    : ${report.all.size}
                 |  No differences     : ${report.noDiff.size}
                 |  With differences   : ${report.hasDiff.size}
                 |  Only in col-1      : ${report.onlyIn1.size}
                 |  Only in col-2      : ${report.onlyIn2.size}
                 |  Cut columns kept   : ${hasDiffCut.headOption
                  .map(_.fields.size)
                  .getOrElse(0)} / ${report.hasDiff.headOption.map(_.fields.size).getOrElse(0)}
                 |  Reports written to : $outDir
                 |""".stripMargin('|'))
  }
}
