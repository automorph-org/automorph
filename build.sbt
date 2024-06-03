// Project
val projectRoot = "org"
val projectName = "automorph"
val projectDomain = s"$projectName.$projectRoot"
val projectDescription = "RPC client and server library for Scala"
val siteUrl = s"https://$projectDomain"
val apiUrl = s"$siteUrl/api"
ThisBuild / homepage := Some(url(siteUrl))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / description := projectDescription
ThisBuild / organization := s"$projectRoot.$projectName"
ThisBuild / organizationName := projectName
ThisBuild / organizationHomepage := Some(url(siteUrl))

ThisBuild / developers := List(Developer(
  id = "m",
  name = "Martin Ockajak",
  email = s"$projectDomain@proton.me",
  url = url(s"https://$projectDomain"),
))
val releaseVersion = settingKey[String]("Release version.")

ThisBuild / releaseVersion := IO.readLines((examples / Compile / scalaSource).value / "examples/Quickstart.scala")
  .filter(_.startsWith(s"//> using dep $projectRoot.$projectName::"))
  .flatMap(_.split(":").lastOption)
  .lastOption.getOrElse("")
val scala3 = settingKey[Boolean]("Uses Scala 3 platform.")
ThisBuild / scala3 := CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3)
Global / onChangedBuildSource := ReloadOnSourceChanges

// Repository
val repositoryPath = s"$projectName-$projectRoot/$projectName"
val repositoryUrl = s"https://github.com/$repositoryPath"
val repositoryShell = s"git@github.com:$repositoryPath.git"
ThisBuild / scmInfo := Some(ScmInfo(url(repositoryUrl), s"scm:$repositoryShell"))
apiURL := Some(url(apiUrl))

onLoadMessage := {
  System.setProperty("project.target", s"${target.value}")
  ""
}

// Structure
lazy val root = project.in(file(".")).settings(
  name := projectName,
  publish / skip := true,
  mimaReportBinaryIssues := {},
  tastyMiMaReportIssues := {},
).aggregate(
  // Core
  meta,
  core,

  // Message codec
  circe,
  jackson,
  playJson,
  weepickle,
  upickle,
  json4s,

  // Effect system
  zio,
  monix,
  catsEffect,
  scalazEffect,

  // Client transport
  sttp,
  rabbitmq,

  // Server transport
  tapir,
  undertow,
  vertx,
  jetty,
  zioHttp,
  akkaHttp,
  pekkoHttp,
  play,

  // Endpoint transport
  finagle,

  // Misc
  default,
  testStandard,
  examples,
)

// Dependencies
def source(project: Project, path: String, dependsOn: ClasspathDep[ProjectReference]*): Project = {
  val subProject = project.in(file(path)).dependsOn(dependsOn: _*).settings(
    Compile / doc / scalacOptions := (if (scala3.value) docScalac3Options else docScalac2Options)
  )
  val directories = path.split('/').toSeq
  directories.headOption.map(Set("examples", "test").contains) match {
    case Some(true) => subProject.settings(
        name := s"$projectName-${directories.mkString("-")}",
        publish / skip := true,
        mimaReportBinaryIssues := {},
        tastyMiMaReportIssues := {},
      )
    case _ =>
      val nameDirectories = directories match {
        case Seq(_) => directories
        case _ => directories.tail
      }
      subProject.settings(
        name := s"$projectName-${nameDirectories.mkString("-")}",
        mimaPreviousArtifacts := Set(organization.value %% name.value % version.value.split("\\+").head),
        tastyMiMaPreviousArtifacts := mimaPreviousArtifacts.value,
      )
  }
}

// Core
val slf4jVersion = "1.7.36"
lazy val meta = source(project, "meta").settings(
  libraryDependencies ++= (
    if (scala3.value) Seq() else Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
  ) ++ Seq("org.slf4j" % "slf4j-api" % slf4jVersion)
)
lazy val core = source(project, "core", meta, testBase % Test)

// Effect system
lazy val zio = source(project, "system/zio", core, testPlugin % Test).settings(
  libraryDependencies += "dev.zio" %% "zio" % "2.1.1"
)
lazy val monix = source(project, "system/monix", core, testPlugin % Test).settings(
  libraryDependencies += "io.monix" %% "monix-eval" % "3.4.1"
)
lazy val catsEffect = source(project, "system/cats-effect", core, testPlugin % Test).settings(
  libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.4"
)
lazy val scalazEffect = source(project, "system/scalaz-effect", core, testPlugin % Test).settings(
  libraryDependencies += "org.scalaz" %% "scalaz-effect" % "7.4.0-M14"
)

// Message codec
val circeVersion = "0.14.7"
lazy val circe = source(project, s"codec/circe", core, testCodec % Test).settings(
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
  )
)
val jacksonVersion = "2.17.1"
lazy val jackson = source(project, "codec/jackson", core, testCodec % Test).settings(
  libraryDependencies ++= Seq(
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion,
  )
)
lazy val json4s = source(project, "codec/json4s", core, testCodec % Test).settings(
  publish / skip := scala3.value,
  libraryDependencies += "org.json4s" %% "json4s-native" % "4.0.7",
)
lazy val playJson = source(project, "codec/play-json", core, testCodec % Test).settings(
  publish / skip := scala3.value,
  libraryDependencies += "org.playframework" %% "play-json" % "3.0.3",
)
lazy val weepickle = source(project, "codec/weepickle", core, testCodec % Test).settings(
  libraryDependencies ++= Seq(
    "com.rallyhealth" %% "weepack-v1" % "1.9.1",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-smile" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-ion" % jacksonVersion,
  )
)
lazy val upickle = source(project, "codec/upickle", core, testCodec % Test).settings(
  libraryDependencies += "com.lihaoyi" %% "upickle" % "3.3.1"
)

// Client transport
val sttpVersion = "3.9.7"
val sttpHttpClientVersion = "3.5.2"
lazy val sttp =
  source(project, "transport/sttp", core, catsEffect % Test, zio % Test, testPlugin % Test).settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion % Test,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion % Test,
      "com.softwaremill.sttp.client3" %% "armeria-backend" % sttpVersion % Test,
      "com.softwaremill.sttp.client3" %% "httpclient-backend" % sttpHttpClientVersion % Test,
      "com.softwaremill.sttp.client3" %% "okhttp-backend" % sttpVersion % Test,
    )
  )
lazy val rabbitmq = source(project, "transport/rabbitmq", core, testPlugin % Test).settings(
  libraryDependencies += "com.rabbitmq" % "amqp-client" % "5.21.0"
)

// Server transport
val tapirVersion = "1.10.8"
lazy val tapir = source(project, "transport/tapir", core, catsEffect % Test, testPlugin % Test).settings(
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-armeria-server" % tapirVersion % Test,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion % Test,
    "org.http4s" %% "http4s-ember-server" % "0.23.27" % Test,
    "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion % Test,
    "com.softwaremill.sttp.tapir" %% "tapir-vertx-server" % tapirVersion % Test,
  )
)
lazy val undertow = source(project, "transport/undertow", core, testPlugin % Test).settings(
  libraryDependencies += "io.undertow" % "undertow-core" % "2.3.13.Final"
)
lazy val vertx = source(project, "transport/vertx", core, testPlugin % Test).settings(
  libraryDependencies += "io.vertx" % "vertx-core" % "4.5.8"
)
val jettyVersion = "11.0.18"
lazy val jetty = source(project, "transport/jetty", core, testPlugin % Test).settings(
  libraryDependencies ++= Seq(
    "org.eclipse.jetty.websocket" % "websocket-jetty-client" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-jetty-server" % jettyVersion,
  )
)
lazy val zioHttp = source(project, "transport/zio-http", core, testPlugin % Test, zio % Test).settings(
  publish / skip := true,
  libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC8"
)
val akkaVersion = "2.8.5"
lazy val akkaHttp = source(project, "transport/akka-http", core, testPlugin % Test).settings(
  Test / fork := true,
  Test / testForkedParallel := true,
  Test / javaOptions ++= testJavaOptions,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http" % "10.5.3",
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Test,
  ),
)
val pekkoVersion = "1.0.2"
lazy val pekkoHttp = source(project, "transport/pekko-http", core, testPlugin % Test).settings(
  Test / fork := true,
  Test / testForkedParallel := true,
  Test / javaOptions ++= testJavaOptions,
  libraryDependencies ++= Seq(
    "org.apache.pekko" %% "pekko-http" % "1.0.1",
    "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion % Test,
  ),
)
val playVersion = "3.0.3"
lazy val play = source(project, "transport/play", core, testPlugin % Test).settings(
  Test / fork := true,
  Test / testForkedParallel := true,
  Test / javaOptions ++= testJavaOptions,
  libraryDependencies ++= Seq(
    "org.playframework" %% "play-server" % playVersion,
    "org.playframework" %% "play-pekko-http-server" % playVersion % Test,
  ),
)

// Endpoint transport
lazy val finagle = source(project, "transport/finagle", core, testPlugin % Test).settings(
  libraryDependencies ++= Seq(
    ("com.twitter" % "finagle-http" % "23.11.0")
      .exclude("org.scala-lang.modules", "scala-collection-compat_2.13")
      .exclude("com.fasterxml.jackson.module", "jackson-module-scala_2.13")
      .cross(CrossVersion.for3Use2_13),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  )
)

// Miscellaneous
lazy val default = source(project, "default", circe, undertow, testPlugin % Test)
lazy val examples = source(
  project,
  "examples",
  default,
  upickle,
  zio,
  vertx,
  sttp,
  rabbitmq,
  testBase % Test,
).settings(
  Test / fork := true,
  Test / javaOptions ++= testJavaOptions,
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,
  ),
  Compile / scalaSource := baseDirectory.value / "project/src/main/scala",
  Test / scalaSource := baseDirectory.value / "project/src/test/scala",
)

// Test
val logbackVersion = "1.5.6"
ThisBuild / Test / testOptions += Tests.Argument("-f", (target.value / "test.results").getPath, "-oDS")
lazy val testBase = source(project, "test/base").settings(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.18",
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0",
    "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "com.lihaoyi" %% "pprint" % "0.9.0",
  )
)
lazy val testCodec = source(project, "test/codec", testBase, meta).settings(
  libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
)
lazy val testPlugin =
  source(project, "test/plugin", testCodec, core, circe, jackson, playJson, json4s, weepickle, upickle)
lazy val testStandard = source(project, "test/standard", testPlugin, core, testPlugin % Test)

def testJavaOptions: Seq[String] =
  Seq(s"-Dproject.target=${System.getProperty("project.target")}")

// Compile
val commonScalacOptions = Seq(
  "-language:higherKinds",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-release",
  "11",
  "-encoding",
  "utf8",
  "-Werror",
)
val commonScalac3Options = commonScalacOptions ++ Seq(
  //  "-explain",
  "-source",
  "3.3",
  "-language:adhocExtensions",
  "-pagewidth",
  "120",
)
val compileScalac2Options = commonScalacOptions ++ Seq(
  //  "-explaintypes",
  "-language:existentials",
  "-Xsource:3",
  "-Xlint:_,-byname-implicit",
  "-Wconf:cat=other-non-cooperative-equals:silent,msg=modifiers are assumed:silent,msg=not suppress any:silent",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wnumeric-widen",
  "-Wunused:imports,patvars,privates,locals,params",
  "-Vfree-terms",
  "-Vimplicits",
  "-Ybackend-parallelism",
  s"${Math.min(java.lang.Runtime.getRuntime.availableProcessors, 16)}",
)
val compileScalac3Options = commonScalac3Options ++ Seq(
  "-indent",
  "-Wconf:msg=not suppress any:silent",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Xcheck-macros",
  "-Xmigration"
)
val docScalac3Options = commonScalac3Options ++ Seq(
  s"-source-links:src=github://$repositoryPath/master",
  s"-skip-by-id:$projectName.client.meta,examples",
)
val docScalac2Options = compileScalac2Options ++ Seq(
  "-skip-packages",
  s"$projectName.client.meta:examples",
)
val exampleScalaVersion = "3.4.2"
ThisBuild / scalaVersion := "3.3.0"
ThisBuild / crossScalaVersions += "2.13.14"
ThisBuild / javacOptions ++= Seq("-source", "11", "-target", "11")
ThisBuild / scalacOptions ++= (if (scala3.value) compileScalac3Options else compileScalac2Options)

// Analyze
scalastyleConfig := baseDirectory.value / "project/scalastyle-config.sbt.xml"
Compile / scalastyleSources ++= (Compile / unmanagedSourceDirectories).value
scalastyleFailOnError := true

// Test
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := (Test / scalastyle).toTask("").value
val testEnvironment = taskKey[Unit]("Prepares testing environment.")
testEnvironment := IO.delete(target.value / "lock")
Test / test := (Test / test).dependsOn(testScalastyle).dependsOn(testEnvironment).value

// Documentation
def flattenTasks[A](tasks: Seq[Def.Initialize[Task[A]]]): Def.Initialize[Task[Seq[A]]] =
  tasks match {
    case Seq() => Def.task(Seq())
    case Seq(head, tail @ _*) => Def.taskDyn(flattenTasks(tail).map(_.+:(head.value)))
  }
lazy val allSources = Def.taskDyn(flattenTasks(root.uses.map(_ / Compile / doc / sources)))
lazy val allTastyFiles = Def.taskDyn(flattenTasks(root.uses.map(_ / Compile / doc / tastyFiles)))
lazy val allDependencyClasspath = Def.taskDyn(flattenTasks(root.uses.map(_ / Compile / doc / dependencyClasspath)))
lazy val docs = project.in(file("site")).settings(
  name := projectName,
  mdocVariables := Map(
    "AUTOMORPH_VERSION" -> releaseVersion.value,
    "SCALA_VERSION" -> exampleScalaVersion,
    "LOGBACK_VERSION" -> logbackVersion,
    "REPOSITORY_URL" -> repositoryUrl,
  ),
  mdocOut := baseDirectory.value / "docs",
  mdocExtraArguments := Seq("--no-link-hygiene"),
  mdoc / fileInputs ++= Seq(
    (LocalRootProject / baseDirectory).value.toGlob / "docs" / ** / "*.md",
    (LocalRootProject / baseDirectory).value.toGlob / "docs" / ** / "*.jpg",
  ),
  Compile / doc / scalacOptions := docScalac3Options,
  Compile / doc / sources ++= allSources.value.flatten.filter(_.getName != "MonixSystem.scala"),
  Compile / doc / tastyFiles ++= allTastyFiles.value.flatten.filter(_.getName != "MonixSystem.tasty"),
  Compile / doc / dependencyClasspath ++=
    allDependencyClasspath.value.flatten.filter(!_.data.getName.startsWith("cats-effect_3-2")),
).enablePlugins(MdocPlugin)

// Site
def relativizeScaladocLinks(content: String, path: String): String = {
  import java.io.File
  val searchData = path.endsWith(s"${File.separator}searchData.js")
  if (searchData || path.endsWith(".html")) {
    val apiLinkPrefix = """"https:\/\/javadoc\.io\/page\/org\.automorph\/[^\/]+\/[^\/]+\/"""
    val patterns = path.split(File.separator).toSeq.init.foldLeft(Seq("")) { case (paths, packageName) =>
      paths :+ s"${paths.last}$packageName/"
    }.reverse
    val replacements = if (searchData) Seq("", s"$apiUrl/") else Range(0, patterns.size).map("../" * _)
    val titledContent = content.replaceAll(">root<", s">${projectName.capitalize}<")
    patterns.zip(replacements).foldLeft(titledContent) { case (text, (pattern, replacement)) =>
      text.replaceAll(s"$apiLinkPrefix$pattern", s""""$replacement""")
    }
  } else {
    content
  }
}

// Generate
val site = taskKey[Unit]("Generates project website.")

site := {
  // Generate Markdown documentation
  import scala.sys.process.Process
  (docs / Compile / doc).value
  (docs / mdoc).toTask("").value

  // Insert examples sources into the examples page
  val docsDirectory = (docs / baseDirectory).value
  val examplesDirectory = (examples / baseDirectory).value / "project"
  insertDocExampleSources(docsDirectory, examplesDirectory, releaseVersion.value, logbackVersion)

  // Generate website
  Process(Seq("yarn", "install"), docsDirectory).!
  Process(Seq("yarn", "build"), docsDirectory, "SITE_DOCS" -> "docs").!

  // Correct API documentation links
  val apiDirectory = docsDirectory / "build/api"
  Path.allSubpaths((docs / Compile / doc / target).value).filter(_._1.isFile).foreach { case (file, path) =>
    IO.write(apiDirectory / path, relativizeScaladocLinks(IO.read(file), path))
  }
  val systemDirectory = s"$projectName/system"
  val monixApiDirectory = (monix / Compile / doc / target).value / systemDirectory
  Path.allSubpaths(monixApiDirectory).filter(_._1.isFile).foreach { case (file, path) =>
    IO.write(apiDirectory / systemDirectory / path, relativizeScaladocLinks(IO.read(file), path))
  }

  // Include example sources
  IO.copyDirectory(examplesDirectory, docsDirectory / "build/examples", overwrite = true)
}

def insertDocExampleSources(
  docsDirectory: File,
  sourceDirectory: File,
  releaseVersion: String,
  logbackVersion: String,
): Unit = {
  val examplesPage = docsDirectory / "docs/Examples.md"
  val examples = IO.readLines(examplesPage).flatMap { docLine =>
    "^###.*https?://[^/]+/[^/]+/([^)]*)\\)".r.findFirstMatchIn(docLine).map(_.group(1)).map { path =>
      var omittedEmptyLines = 3
      val lines = IO.readLines(sourceDirectory / path)
      val caption = lines.headOption.filter(_.startsWith("// ")).map(_.replaceFirst("// ", "") + "\n")
      val source = caption.map(_ => lines.tail).getOrElse(lines).flatMap {
        case line if line.startsWith("//> ") =>
          Seq(line
            .replaceFirst("//> using ", "  \"")
            .replaceFirst("::", "\" %% \"")
            .replaceAll(":", "\" % \"")
            .replaceFirst("@AUTOMORPH_VERSION@", releaseVersion)
            .replaceFirst("@SCALA_VERSION@", exampleScalaVersion)
            .replaceFirst("@LOGBACK_VERSION@", logbackVersion)
            .replaceFirst("@STTP_VERSION@", sttpVersion)
            .replaceFirst("$", "\","))
        case line if line.startsWith("package") => Seq(")\n```\n\n**Source**\n```scala")
        case line if line.startsWith("private") && line.contains("object") => Seq()
        case line if line.contains("def main(") => Seq()
        case line if line.contains("@scala") => Seq()
        case "  }" => Seq()
        case "}" => Seq()
        case "" => if (omittedEmptyLines <= 0) Seq("")
          else {
            omittedEmptyLines -= 1
            Seq()
          }
        case line => Seq(line.replaceFirst("    ", "").replaceFirst("^private\\[examples\\] ", ""))
      }
      Seq(docLine) ++ caption ++ Seq("\n", "**Build**\n```scala\nlibraryDependencies ++= Seq(") ++ source ++ Seq("```")
    }.getOrElse(Seq(docLine))
  }
  IO.writeLines(examplesPage, examples)
}

// Start
val startSite = taskKey[Unit]("Continuously generates project website.")

startSite := {
  import scala.sys.process.Process
  Process(Seq("yarn", "install"), (docs / baseDirectory).value).!
  Process(Seq("yarn", "start"), (docs / baseDirectory).value, "SITE_DOCS" -> "docs").!
}
startSite := startSite.dependsOn(site).value

// Serve
val serveSite = taskKey[Unit]("Serve generated project website.")

serveSite := {
  import scala.sys.process.Process
  Process(Seq("yarn", "install"), (docs / baseDirectory).value).!
  Process(Seq("yarn", "serve"), (docs / baseDirectory).value, "SITE_DOCS" -> "docs").!
}
serveSite := serveSite.dependsOn(site).value

cleanFiles ++= Seq(
  (docs / baseDirectory).value / "build",
  (docs / baseDirectory).value / "docs",
  (docs / baseDirectory).value / "static/examples",
)

// Publish
def environment(name: String): String =
  Option(System.getenv(name)).getOrElse("")
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
sonatypeCredentialHost := "s01.oss.sonatype.org"

credentials ++= Seq(
  Credentials(
    "GnuPG Key ID",
    "gpg",
    environment("ARTIFACT_GPG_KEY_ID"),
    "",
  ),
  Credentials(
    "Sonatype Nexus Repository Manager",
    "s01.oss.sonatype.org",
    projectDomain,
    environment("SONATYPE_PASSWORD"),
  ),
)
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible
