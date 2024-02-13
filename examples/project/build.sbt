// Project
scalaVersion := "3.3.1"
name := "automorph-example"
organization := "example"

// Dependencies
libraryDependencies ++= {
  // Set the library version to the latest version control tag
  val automorphVersion = version.value.split("\\+").head
  val sttpVersion = "3.9.2"
  Seq(
    // Default
    "org.automorph" %% "automorph-default" % automorphVersion,
    "ch.qos.logback" % "logback-classic" % "1.4.14",

    // Plugins
    "org.automorph" %% "automorph-rabbitmq" % automorphVersion,
    "org.automorph" %% "automorph-sttp" % automorphVersion,
    "org.automorph" %% "automorph-upickle" % automorphVersion,
    "org.automorph" %% "automorph-vertx" % automorphVersion,
    "org.automorph" %% "automorph-zio" % automorphVersion,

    // Transport
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,

    // Test
    "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  )
}

// Native Image
val initializeAtBuildTime = Seq(
  "org.slf4j.LoggerFactory",
  "org.slf4j.helpers",
  "ch.qos.logback.classic.Logger",
  "ch.qos.logback.classic.LoggerContext",
  "ch.qos.logback.classic.spi.LogbackServiceProvider",
  "ch.qos.logback.classic.util.LogbackMDCAdapter",
  "ch.qos.logback.core.status.InfoStatus",
  "ch.qos.logback.core.util.Duration",
)
enablePlugins(NativeImagePlugin)
Compile / mainClass := Some("examples.Quickstart")
nativeImageInstalled := true
nativeImageOptions ++= Seq(
  "-O3",
  "--gc=G1",
  "--no-fallback",
  "--strict-image-heap",
  "--report-unsupported-elements-at-runtime",
  s"-H:ConfigurationFileDirectories=${(Compile / resourceDirectory).value}",
  s"--initialize-at-build-time=${initializeAtBuildTime.mkString(",")}",
  s"--parallelism=${java.lang.Runtime.getRuntime.availableProcessors}",
)


// Test
Test / parallelExecution := false

