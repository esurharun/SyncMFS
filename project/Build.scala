import sbt._
import Keys._
import com.github.retronym.SbtOneJar

object SyncmfsBuild extends Build
{

	def standardSettings = Seq(
    	exportJars := true,

    	name := "Syncmfs",

		version := "0.0.1",

		scalaVersion := "2.9.0-1",

		scalacOptions += "-deprecation",

		libraryDependencies += "org.mongodb" % "mongo-java-driver" % "2.6.5",

		libraryDependencies += "log4j" % "log4j" % "1.2.16",

		libraryDependencies += "com.codahale" % "logula_2.9.0-1" % "2.1.3" from "http://repo.codahale.com/com/codahale/logula_2.9.0-1/2.1.3/logula_2.9.0-1-2.1.3.jar",

		libraryDependencies += "com.github.scopt" % "scopt" % "1.1.1" from "http://nexus.scala-tools.org/content/repositories/releases/com/github/scopt/scopt_2.9.0-1/1.1.1/scopt_2.9.0-1-1.1.1.jar",

		mainClass in (Compile, run) := Some("com.synchmfs.Syncmfs"),

		mainClass in (Compile, packageBin) := Some("com.synchmfs.Syncmfs")
  	) ++ Defaults.defaultSettings

	lazy val root = Project("SyncMFS", file("."), settings = standardSettings ++ SbtOneJar.oneJarSettings)


}