import sbt._
import Keys._

object MediachainBuild extends Build {
  updateOptions := updateOptions.value.withCachedResolution(true)

  val specs2Version = "3.7.3"
  val scalaCheckVersion = "1.13.1"

  override lazy val settings = super.settings ++ Seq(
    organization := "io.mediachain",
    version := "0.0.1",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-junit" % specs2Version % "test",
      "org.specs2" %% "specs2-matcher-extra" % specs2Version % "test",
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0-RC1"
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  lazy val utils = Project("utils", file("utils"))
    .settings(settings)

  lazy val transactor = Project("transactor", file("transactor"))
    .settings(settings)
    .dependsOn(utils)

  lazy val peer = Project("peer", file("peer"))
    .settings(settings)

  lazy val client = Project("client", file("client"))
    .settings(settings)
}
