ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"
libraryDependencies += "com.interactivebrokers" % "tws-api" % "9.73.01-SNAPSHOT"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.3.5"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
libraryDependencies += "org.json" % "json" % "20210307"
resolvers += Resolver.mavenLocal

lazy val root = (project in file("."))
  .settings(
    name := "RobotTrader"
  )

