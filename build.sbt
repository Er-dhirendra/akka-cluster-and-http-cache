import scala.collection.Seq

// Global project settings
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

// Dependency versions
val AkkaVersion             = "2.8.8"
val AkkaHttpVersion         = "10.2.7"
val AkkaDiagnosticsVersion  = "2.0.1"
val LogbackClassicVersion   = "1.5.18"
val ScalaTestVersion        = "3.2.17"
val MUnitVersion            = "1.0.0-M10"
val JacksonSupportVersion   = "1.39.2"

lazy val root = (project in file("."))
  .settings(
    name := "akka-cluster-and-http-cache",

    libraryDependencies ++= Seq(
      // Akka core & cluster
      "com.typesafe.akka" %% "akka-actor-typed"           % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed"         % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,

      // Akka HTTP core
      "com.typesafe.akka" %% "akka-http"                  % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"       % AkkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-jackson"          % JacksonSupportVersion,

      // Logging
      "ch.qos.logback"     % "logback-classic"            % LogbackClassicVersion,

      // Diagnostics
      "com.lightbend.akka" %% "akka-diagnostics"          % AkkaDiagnosticsVersion,

      // Test dependencies
      "org.scalatest"             %% "scalatest"               % ScalaTestVersion % Test,
      "org.scalameta"             %% "munit"                   % MUnitVersion     % Test,
      "com.typesafe.akka"         %% "akka-http-testkit"       % AkkaHttpVersion  % Test,
      "com.typesafe.akka"         %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
      "com.typesafe.akka"         %% "akka-multi-node-testkit"  % AkkaVersion     % Test
    )
  )
