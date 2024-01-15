ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(name := "B4-processor")

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
  "-Ymacro-annotations",
  "-JXss512m",
  "-JXmx2G"
)

Test / logBuffered := false
//Test / parallelExecution := false

scalaVersion := "2.13.12"

addCompilerPlugin(
  "org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full,
)
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "5.1.0",
  "edu.berkeley.cs" %% "chiseltest" % "5.0.2" % "test",
)
