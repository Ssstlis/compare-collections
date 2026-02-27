Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / parallelExecution := false

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, LocalDeployPlugin, BuildInfoSpecifiedPlugin)
  .settings(
    scalacOptions := Seq(
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
      "com.typesafe"       % "config"             % "1.4.6",
      "org.apache.poi"     % "poi"                % "5.5.1",
      "org.apache.poi"     % "poi-ooxml"          % "5.5.1",
      "me.tongfei"         % "progressbar"        % "0.10.2",
      "org.typelevel"     %% "cats-core"          % "2.13.0",
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.6.4",
      "com.github.scopt"  %% "scopt"              % "4.1.0",

      "io.circe"      %% "circe-core"   % "0.14.15",
      "io.circe"      %% "circe-parser" % "0.14.15",
      "org.scalatest" %% "scalatest"    % "3.2.19" % Test
    )
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
