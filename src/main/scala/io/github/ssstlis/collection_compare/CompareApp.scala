package io.github.ssstlis.collection_compare

import io.github.ssstlis.collection_compare.config.CliParser
import io.github.ssstlis.collection_compare.mongo.{DocumentProcessor, MongoConfig, MongoService}

object CompareApp extends App {
  CliParser.parse(args) match {
    case None =>
      sys.exit(1)

    case Some(cfg) =>
     println(s"""|=== MongoDB Collection Comparison Tool ===
                 |  Config key 1       : ${cfg.host1.getOrElse(MongoConfig.defaultKey)}
                 |  Config key 2       : ${cfg.host2.getOrElse(MongoConfig.defaultKey)}
                 |  DB 1               : ${cfg.db1}
                 |  DB 2               : ${cfg.db2}
                 |  Collection 1       : ${cfg.collection1}
                 |  Collection 2       : ${cfg.collection2}
                 |  Filter             : ${cfg.filter}
                 |  Request timeout    : ${cfg.requestTimeout}
                 |  Exclude fields     : ${if (cfg.excludeFields.isEmpty) "-" else cfg.excludeFields.mkString(", ")}
                 |  Projection excl.   : ${if (cfg.projectionExclude.isEmpty) "-" else cfg.projectionExclude.mkString(", ")}
                 |  Round precision    : ${cfg.roundPrecision}
                 |  Key                : ${cfg.key.mkString(", ")}
                 |  Exclude from cut   : ${if (cfg.excludeFromCut.isEmpty) "-" else cfg.excludeFromCut.mkString(", ")}
                 |  Sort               : ${if (cfg.sortBy.isEmpty) "-" else cfg.sortBy.map(_.asInfoString).mkString(", ")}
                 |  Output path        : ${cfg.outputPath}
                 |  Reports            : ${cfg.reports.mkString(", ")}
                 |  Formulas delimiter : ${cfg.excelFormulasDelimiter}
                 |""".stripMargin('|'))

      val config1 = MongoConfig.load(cfg.host1.getOrElse(MongoConfig.defaultKey))
      val config2 = MongoConfig.load(cfg.host2.getOrElse(MongoConfig.defaultKey))

      val mongo1 = new MongoService(config1, cfg.db1, cfg.requestTimeout)
      val mongo2 = new MongoService(config2, cfg.db2, cfg.requestTimeout)

      val processor = new DocumentProcessor(mongo1, mongo2)

      val report =
        try processor.compareCollections(cfg)
        finally { mongo1.close(); mongo2.close() }

      val hasDiffCut = processor.applyCut(report.hasDiff, cfg.key, cfg.excludeFromCut)
      //format:off
      val outDir = ReportOrchestrator.write(
        report, hasDiffCut, cfg.outputPath, cfg.reports,
        cfg.host1.getOrElse(MongoConfig.defaultKey), cfg.collection1,
        cfg.host2.getOrElse(MongoConfig.defaultKey), cfg.collection2,
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
