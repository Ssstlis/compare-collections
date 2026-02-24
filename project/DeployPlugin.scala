import Key.{buildCommit, buildTime}
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.*
import sbt.*
import sbt.Keys.*
import sbt.internal.util.ManagedLogger

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.collection.JavaConverters.*

object DeployPlugin extends AutoPlugin {

  private val DEPLOY_PATH_ENV_NAME = "SDP_SCALA_APP_DEPLOY_PATH"
  private val LINK_PATH_ENV_NAME = "SDP_SCALA_APP_DEPLOY_LINK_PATH"

  override def requires: Plugins = UniversalPlugin && BuildInfoSpecifiedPlugin
  override def trigger           = noTrigger

  object autoImport {
    val deploy = inputKey[Unit](
      "Stage and deploy the distribution.\n" +
        "Usage: deploy <deployPath> <linkPath>\n" +
        s"  deployPath — root dir, default value from env $DEPLOY_PATH_ENV_NAME; distributable is placed at <deployPath>/<name>-<version>/\n" +
        s"  linkPath   — dir where bin/* scripts are symlinked, default value from env $LINK_PATH_ENV_NAME"
    )
    val deployInfo = inputKey[Unit](
      "Show where is installation would take, where to place, where to link.\n" +
        "Usage: deploy <deployPath> <linkPath>\n" +
        s"  deployPath — root dir, default value from env $DEPLOY_PATH_ENV_NAME; distributable is placed at <deployPath>/<name>-<version>/\n" +
        s"  linkPath   — dir where bin/* scripts are symlinked, default value from env $LINK_PATH_ENV_NAME"
    )
    val staleInstallations = inputKey[Unit](
      "Shows stale installations (older than 30 days).\n" +
        "Usage: staleInstallations <deployPath>\n" +
        s"  deployPath — root dir, default value from env $DEPLOY_PATH_ENV_NAME\n"
    )
  }

  import autoImport.*

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    deploy := deployTaskImpl.evaluated,
    deployInfo := deployInfoTaskImpl.evaluated,
    staleInstallations := staleInstallationsTaskImpl.evaluated
  )

  // ── helpers ──────────────────────────────────────────────────────────────

  private def formatBytes(bytes: Long): String = {
    val kb = 1024L; val mb = kb * 1024; val gb = mb * 1024
    if (bytes >= gb) f"${bytes.toDouble / gb}%.1f GB"
    else if (bytes >= mb) f"${bytes.toDouble / mb}%.1f MB"
    else if (bytes >= kb) f"${bytes.toDouble / kb}%.1f KB"
    else s"$bytes B"
  }

  private def copyDir(src: Path, dest: Path): Unit = {
    val stream = Files.walk(src)
    try
      stream.iterator().asScala.foreach { source =>
        val target = dest.resolve(src.relativize(source))
        if (Files.isDirectory(source)) Files.createDirectories(target)
        else {
          Files.createDirectories(target.getParent)
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        }
      }
    finally stream.close()
  }

  private def dirSize(root: Path): Long = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(p => Files.isRegularFile(p)).map(p => Files.size(p)).sum
    finally stream.close()
  }

  private val DATE_TIME_FORMATTER = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
  }


  private def getEnv(envName: String, log: ManagedLogger) = {
    sys.env.get(envName) match {
      case Some(value) => value
      case None        =>
        log.err(s"$envName env not set")
        null
    }
  }


  private case class DeployCtx(
    stageDir: Path,
    pName:    String,
    pVersion: String,
    pCommit:  String,
    pTime:    String,
    log:      ManagedLogger
  ) {
    def dirName: String = s"$pName-$pVersion-$pTime-$pCommit"
  }

  private val deployCtx: Def.Initialize[Task[DeployCtx]] = Def.task {
    DeployCtx(
      stageDir = (Universal / stage).value.toPath,
      pName    = name.value,
      pVersion = version.value,
      pCommit  = buildCommit.value.take(8),
      pTime    = DATE_TIME_FORMATTER.format(buildTime.value),
      log      = streams.value.log
    )
  }

  private lazy val isWindows = System.getProperty("os.name").toLowerCase.contains("win")

  private def staleInstallationsDef(deployRoot: Path, projectName: String, filter: Path => Boolean, log: ManagedLogger): Unit = {
    if (Files.exists(deployRoot) && Files.isDirectory(deployRoot)) {
      val thirtyDaysAgo = Instant.now().plus(30, ChronoUnit.DAYS)
      val stream        = Files.list(deployRoot)
      val stale         = try {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isDirectory(p) && filter(p) && p.getFileName.toString.startsWith(projectName))
          .flatMap { p =>
            val mtime = Files.getLastModifiedTime(p).toInstant
            if (mtime.isBefore(thirtyDaysAgo))
              Some(p -> ChronoUnit.DAYS.between(mtime, Instant.now()))
            else None
          }
          .toList
          .sortBy(-_._2)
      } finally stream.close()

      if (stale.nonEmpty) {
        log.warn(s"Stale deployments in $deployRoot (older than 30 days):")
        stale.foreach { case (p, days) =>
          log.warn(s"  ${p.getFileName}  ($days days ago)")
        }
      }
    }
  }

  private def installApp(stageDir: Path, destDir: Path, log: ManagedLogger): Unit = {
    if (Files.exists(destDir)) {
      val stream = Files.walk(destDir)
      try
        stream
          .sorted(java.util.Comparator.reverseOrder[Path]())
          .iterator()
          .asScala
          .foreach(p => Files.delete(p))
      finally stream.close()
    }
    copyDir(stageDir, destDir)
    log.info(s"Deployed  : ${scala.Console.CYAN}$destDir${scala.Console.RESET}  (${scala.Console.GREEN}${formatBytes(dirSize(destDir))}${scala.Console.RESET})")
  }

  private def symlinks(linkRoot: Path, stageBinDir: Path, destBinDir: Path, isWindows: Boolean): List[(Path, Path)] = {
    if (Files.exists(stageBinDir)) {
      val stream = Files.list(stageBinDir)
      try
        stream
          .iterator()
          .asScala
          .toList
          .flatMap { bin =>
            val fileName = bin.getFileName.toString
            val isScript = if (isWindows) fileName.endsWith(".bat") else !fileName.endsWith(".bat")
            if (isScript) Some(linkRoot.resolve(fileName) -> destBinDir.resolve(fileName))
            else None
          }
      finally stream.close()
    } else Nil
  }

  private def symlinkBinary(linkRoot: Path, stageBinDir: Path, destBinDir: Path, isWindows: Boolean, log: ManagedLogger): Unit = {
    Files.createDirectories(linkRoot)
    symlinks(linkRoot, stageBinDir, destBinDir, isWindows).foreach { case (link, bin) =>
      if (Files.exists(link) || Files.isSymbolicLink(link)) Files.delete(link)
      Files.createSymbolicLink(link, bin)
      log.info(s"Linked    : ${scala.Console.CYAN}$link${scala.Console.RESET}  →  ${scala.Console.CYAN}$bin${scala.Console.RESET}")
    }
  }

  // ── task ─────────────────────────────────────────────────────────────────

  private lazy val deployTaskImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import sbt.complete.DefaultParsers.*

    val (deployArgOpt, linkPathOpt) = ((Space.? ~> StringBasic.?) ~ (Space.? ~> StringBasic.?)).parsed

    val ctx = deployCtx.value
    import ctx.{stageDir, dirName, pName, log}

    lazy val deployRootFromEnv = getEnv(DEPLOY_PATH_ENV_NAME, log)
    lazy val linkRootFromEnv   = getEnv(LINK_PATH_ENV_NAME, log)

    val deployRoot = Paths.get(deployArgOpt.getOrElse(deployRootFromEnv))
    val linkRoot   = Paths.get(linkPathOpt.getOrElse(linkRootFromEnv))
    val destDir    = deployRoot.resolve(dirName)

    // ── 1. Copy stageDir → destDir ──────────────────────────────────────────
    installApp(stageDir, destDir.resolve("bin"), log)

    // ── 2. Symlink bin/* → linkRoot/ ────────────────────────────────────────
    symlinkBinary(linkRoot, stageDir.resolve("bin"), destDir.resolve("bin"), isWindows, log)

    // ── 3. Warn about stale deployments (> 30 days old) ─────────────────────
    staleInstallationsDef(deployRoot, pName, _ != destDir, log)
  }

  private lazy val deployInfoTaskImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import sbt.complete.DefaultParsers.*

    val (deployArgOpt, linkPathOpt) = ((Space.? ~> StringBasic.?) ~ (Space.? ~> StringBasic.?)).parsed

    val ctx = deployCtx.value
    import ctx.{stageDir, log}

    lazy val deployRootFromEnv = getEnv(DEPLOY_PATH_ENV_NAME, log)
    lazy val linkRootFromEnv   = getEnv(LINK_PATH_ENV_NAME, log)

    val deployRoot = Paths.get(deployArgOpt.getOrElse(deployRootFromEnv))
    val linkRoot   = Paths.get(linkPathOpt.getOrElse(linkRootFromEnv))
    val destDir    = deployRoot.resolve(ctx.dirName)

    log.info(s"Stage path: ${scala.Console.CYAN}${stageDir.toAbsolutePath}${scala.Console.RESET}")
    log.info(s"Deploy path: ${scala.Console.YELLOW}${destDir.toAbsolutePath}${scala.Console.RESET}")
    symlinks(linkRoot, stageDir.resolve("bin"), destDir.resolve("bin"), isWindows).foreach { case (link, d) =>
      log.info(s"To be linked: ${scala.Console.YELLOW}$link${scala.Console.RESET}  →  ${scala.Console.YELLOW}$d${scala.Console.RESET}")
    }
  }

  private lazy val staleInstallationsTaskImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import sbt.complete.DefaultParsers.*

    val deployArgOpt = (Space.? ~> StringBasic.?).parsed
    val ctx = deployCtx.value
    import ctx.{pName, log}

    lazy val deployRootFromEnv = getEnv(DEPLOY_PATH_ENV_NAME, log)

    val deployRoot = Paths.get(deployArgOpt.getOrElse(deployRootFromEnv))
    staleInstallationsDef(deployRoot, pName, _ => true, log)
  }
}
