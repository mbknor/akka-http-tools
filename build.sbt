import ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport.releaseStepCommand

enablePlugins(PackPlugin)

lazy val commonSettings = Seq(
  organization := "com.kjetland.akka-http-tools",
  organizationName := "kjetland.com",
  scalaVersion := "2.12.6",
  pomIncludeRepository := { _ => false },
  compileOrder in Test := CompileOrder.Mixed,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  releaseCrossBuild := false,
  scalacOptions in(Compile, doc) ++= Seq(scalaVersion.value).flatMap {
    case v if v.startsWith("2.12") =>
      Seq("-no-java-comments") //workaround for scala/scala-dev#249
    case _ =>
      Seq()
  }
)


val metricsVersion = "3.2.5"
val akkaVersion = "2.5.15"
val akkaHttpVersion = "10.1.5"
val swaggerAkkaHttpVersion = "0.14.0"
val akkaToolsVersion = "2.5.0.1-SNAPSHOT"
val jacksonVersion = "2.9.4"
val scalaLoggingVersion = "3.9.0"
val scalaTestVersion = "3.0.4"


lazy val commonDeps = Seq(
  "com.google.guava" %  "guava" % "25.1-jre",
  "com.typesafe.scala-logging" %%  "scala-logging" % scalaLoggingVersion,
  "org.scalatest" %%  "scalatest" % scalaTestVersion % "test",
  "org.mockito" %  "mockito-core" % "2.18.3" % "test",
  "ch.qos.logback" %  "logback-classic" % "1.2.3" % "test"
)

lazy val akkaDeps = Seq(
  "com.typesafe.akka" %%  "akka-actor" % akkaVersion % "provided",
  "com.typesafe.akka" %%  "akka-slf4j" % akkaVersion % "provided",
  "com.typesafe.akka" %%  "akka-stream" % akkaVersion % "provided",
  "com.typesafe.akka" %%  "akka-http" % akkaHttpVersion % "provided",
  "com.typesafe.akka" %%  "akka-http-caching" % akkaHttpVersion % "provided",
  "com.typesafe.akka" %%  "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %%  "akka-stream-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %%  "akka-http-testkit" % akkaHttpVersion % "test",
)

lazy val jwtDeps = Seq(
  "com.auth0" % "jwks-rsa" % "0.6.0",
  "io.jsonwebtoken" % "jjwt-api" % "0.10.5",
  "io.jsonwebtoken" % "jjwt-impl" % "0.10.5",
  "io.jsonwebtoken" % "jjwt-jackson" % "0.10.5"
)

lazy val jacksonDeps = Seq(
  "com.fasterxml.jackson.core" %  "jackson-core" % jacksonVersion % "provided",
  "com.fasterxml.jackson.datatype" %  "jackson-datatype-jdk8" % jacksonVersion % "provided",
  "com.fasterxml.jackson.datatype" %  "jackson-datatype-jsr310" % jacksonVersion % "provided",
  "com.fasterxml.jackson.core" %  "jackson-databind" % jacksonVersion % "provided",
  "com.fasterxml.jackson.dataformat" %  "jackson-dataformat-yaml" % jacksonVersion % "provided",
  "com.fasterxml.jackson.module" %%  "jackson-module-scala" % jacksonVersion % "provided"
)

lazy val deps  = Seq(

  "ch.megard" %%  "akka-http-cors" % "0.3.0",
  "com.github.swagger-akka-http" %%  "swagger-akka-http" % swaggerAkkaHttpVersion,
  "io.kamon" %%  "kamon-core" % "1.1.3",
  "io.kamon" %%  "kamon-logback" % "1.0.3",
  "io.kamon" %%  "kamon-akka-2.5" % "1.1.2",
  "io.kamon" %%  "kamon-prometheus" % "1.1.1",
  "io.kamon" %%  "kamon-akka-http-2.5" % "1.1.0",
  "org.aspectj" %  "aspectjweaver" % "1.9.1",
  "junit"             %  "junit"       % "4.12"  % "test"
)

lazy val root = (project in file("."))
  .settings(name := "akka-http-tools-parent")
  .settings(commonSettings: _*)
  .aggregate(
    akkaHttpToolsCore,
    )

lazy val akkaHttpToolsCore = (project in file("akka-http-tools-core"))
  .settings(name := "akka-http-tools-core")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= (commonDeps))
  .settings(libraryDependencies ++= (akkaDeps))
  .settings(libraryDependencies ++= (jacksonDeps))

lazy val akkaHttpToolsJwt = (project in file("akka-http-tools-jwt"))
  .settings(name := "akka-http-tools-jwt")
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= (commonDeps))
  .settings(libraryDependencies ++= (akkaDeps))
  .settings(libraryDependencies ++= (jwtDeps))



releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  tagRelease
)
