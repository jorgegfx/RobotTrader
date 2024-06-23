ThisBuild / version := "0.1.0-SNAPSHOT"
scalaVersion := "3.4.2"

// ZIO Version
val zioVersion = "2.0.0"

// Doobie Version
val doobieVersion = "1.0.0-RC5"

// ZIO JSON Version
val zioJsonVersion = "0.3.0"

// MySQL Connector Version
val mysqlConnectorVersion = "8.0.30"
//libraryDependencies += "com.interactivebrokers" % "tws-api" % "9.73.01-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.3.5",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.json" % "json" % "20210307",
  // ZIO Core
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,

  // ZIO JSON
  "dev.zio" %% "zio-json" % zioJsonVersion,
  "io.github.gaelrenoux" %% "tranzactio-doobie" % "5.2.0",
  // Doobie Core
  // Start with this one
  "org.tpolecat" %% "doobie-core" % doobieVersion,

  // And add any of these as needed
  "org.tpolecat" %% "doobie-h2" % doobieVersion, // H2 driver 1.4.200 + type mappings.
  "org.tpolecat" %% "doobie-hikari" % doobieVersion, // HikariCP transactor.
  "org.tpolecat" %% "doobie-specs2" % doobieVersion % "test", // Specs2 support for typechecking statements.
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion % "test",

// ZIO Interop Cats
  "dev.zio" %% "zio-interop-cats" % "3.2.9.1",

  // MySQL Connector
  "mysql" % "mysql-connector-java" % mysqlConnectorVersion,

  // Testing
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "RobotTrader"
  )

