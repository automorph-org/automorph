scalaVersion := "3.3.0"
name := "automorph"
organization := "example"

libraryDependencies ++= {
  // Set the library version to the latest version control tag
  val automorphVersion = version.value.split("\\+").head
  val sttpVersion = "3.9.0"
  Seq(
    // Default
    "org.automorph" %% "automorph-default" % automorphVersion,
    "ch.qos.logback" % "logback-classic" % "1.4.11",

    // Plugins
    "org.automorph" %% "automorph-rabbitmq" % automorphVersion,
    "org.automorph" %% "automorph-sttp" % automorphVersion,
    "org.automorph" %% "automorph-upickle" % automorphVersion,
    "org.automorph" %% "automorph-vertx" % automorphVersion,
    "org.automorph" %% "automorph-zio" % automorphVersion,

    // Transport
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,
    "io.arivera.oss" % "embedded-rabbitmq" % "1.5.0",

    // Test
    "org.scalatest" %% "scalatest" % "3.2.16" % Test
  )
}

Test / parallelExecution := false

