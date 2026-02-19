Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / parallelExecution := false

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
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
    name := "compare-collections",
    libraryDependencies ++= Seq(
      "org.apache.poi" % "poi"       % "5.5.1",
      "org.apache.poi" % "poi-ooxml" % "5.5.1",
      "com.typesafe"   % "config"    % "1.4.5",
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.6.3",
    )
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")