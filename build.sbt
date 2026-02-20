import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.collection.JavaConverters._

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / parallelExecution := false

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

// ── build helpers ────────────────────────────────────────────────────────────

def formatBytes(bytes: Long): String = {
  val kb = 1024L; val mb = kb * 1024; val gb = mb * 1024
  if (bytes >= gb) f"${bytes.toDouble / gb}%.1f GB"
  else if (bytes >= mb) f"${bytes.toDouble / mb}%.1f MB"
  else if (bytes >= kb) f"${bytes.toDouble / kb}%.1f KB"
  else s"$bytes B"
}

/** Recursively copies src into dest, preserving file attributes (incl. execute bits). */
def copyDir(src: Path, dest: Path): Unit = {
  val stream = Files.walk(src)
  try stream.iterator().asScala.foreach { source =>
    val target = dest.resolve(src.relativize(source))
    if (Files.isDirectory(source)) Files.createDirectories(target)
    else {
      Files.createDirectories(target.getParent)
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }
  } finally stream.close()
}

/** Returns total size in bytes of all regular files under root. */
def dirSize(root: Path): Long = {
  val stream = Files.walk(root)
  try stream.iterator().asScala.filter(p => Files.isRegularFile(p)).map(p => Files.size(p)).sum
  finally stream.close()
}

// ── keys ─────────────────────────────────────────────────────────────────────

lazy val deploy = inputKey[Unit](
  "Stage and deploy the distribution.\n" +
  "Usage: deploy <deployPath> <linkPath>\n" +
  "  deployPath — root dir; distributable is placed at <deployPath>/<name>-<version>/\n" +
  "  linkPath   — dir where bin/* scripts are symlinked"
)

/** Reusable deploy task implementation.
  * Attach to any project with a single line: `deploy := deployTask.evaluated`
  */
lazy val deployTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  import sbt.complete.DefaultParsers._

  val (deployArg, linkArg) =
    ((Space ~> StringBasic) ~ (Space ~> StringBasic)).parsed

  val stageDir  = (Universal / stage).value.toPath
  val pName     = name.value
  val pVersion  = version.value
  val log       = streams.value.log
  val isWindows = System.getProperty("os.name").toLowerCase.contains("win")

  val deployRoot = Paths.get(deployArg)
  val linkRoot   = Paths.get(linkArg)
  val destDir    = deployRoot.resolve(s"$pName-$pVersion")

  // ── 1. Copy stageDir → destDir ────────────────────────────────────────────
  if (Files.exists(destDir)) {
    val stream = Files.walk(destDir)
    try stream.sorted(java.util.Comparator.reverseOrder[Path]())
              .iterator().asScala
              .foreach(p => Files.delete(p))
    finally stream.close()
  }
  copyDir(stageDir, destDir)

  log.info(s"Deployed  : $destDir  (${formatBytes(dirSize(destDir))})")

  // ── 2. Symlink bin/* → linkRoot/ ──────────────────────────────────────────
  Files.createDirectories(linkRoot)
  val binDir = destDir.resolve("bin")
  if (Files.exists(binDir)) {
    val stream = Files.list(binDir)
    try stream.iterator().asScala.foreach { bin =>
      val fileName = bin.getFileName.toString
      val isScript = if (isWindows) fileName.endsWith(".bat") else !fileName.endsWith(".bat")
      if (isScript) {
        val link = linkRoot.resolve(fileName)
        if (Files.exists(link) || Files.isSymbolicLink(link)) Files.delete(link)
        Files.createSymbolicLink(link, bin.toAbsolutePath)
        log.info(s"Linked    : $link  →  ${bin.toAbsolutePath}")
      }
    } finally stream.close()
  }

  // ── 3. Warn about stale deployments in deployRoot (> 30 days old) ─────────
  if (Files.exists(deployRoot) && Files.isDirectory(deployRoot)) {
    val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)
    val stream        = Files.list(deployRoot)
    val stale = try {
      stream.iterator().asScala
        .filter(p => Files.isDirectory(p) && p != destDir)
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

// ── project ───────────────────────────────────────────────────────────────────

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    scalacOptions := Seq(
      "-release:11",
      "-g:vars",
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:implicitConversions",
      "-language:existentials",
      "-language:higherKinds",
      "-language:postfixOps",
      "-Xlint:infer-any",
      "-Xlint:missing-interpolator",
      "-Wunused:imports",
      "-encoding",
      "utf8"
    ),
    name                := "compare-collections",
    Compile / mainClass := Some("io.github.ssstlis.collection_compare.CompareApp"),
    libraryDependencies ++= Seq(
      "ch.qos.logback"     % "logback-classic"    % "1.5.32",
      "org.slf4j"          % "log4j-over-slf4j"   % "2.0.17",
      "com.typesafe"       % "config"             % "1.4.5",
      "org.apache.poi"     % "poi"                % "5.5.1",
      "org.apache.poi"     % "poi-ooxml"          % "5.5.1",
      "me.tongfei"         % "progressbar"        % "0.10.2",
      "org.typelevel"     %% "cats-core"          % "2.13.0",
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.6.3",
      "com.github.scopt"  %% "scopt"              % "4.1.0",
      "org.scalatest"     %% "scalatest"          % "3.2.19" % Test
    ),
    deploy := deployTask.evaluated
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
