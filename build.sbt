name := """StateCalculation"""
organization := "stategraph"

version := "1.0-SNAPSHOT"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test

lazy val domain = (project in file("domain"))

lazy val web = (project in file(".")).dependsOn(domain).enablePlugins(PlayScala)