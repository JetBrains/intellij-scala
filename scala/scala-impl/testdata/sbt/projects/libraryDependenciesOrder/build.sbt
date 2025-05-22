import scala.collection.immutable.Seq

ThisBuild / scalaVersion := "2.13.14"

lazy val core = (project in file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.10.0",
      "javax.servlet" % "javax.servlet-api" % "4.0.1" % Runtime ,
      "org.scalameta" %% "munit" % "1.0.0-M9" % Test
    )
  )

lazy val api = (project in file("api"))
  .dependsOn(core % "runtime -> runtime")
  .settings(
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.4.0" % Provided,
  )

lazy val service = (project in file("service"))
  .dependsOn(api % "test -> test")
  .settings(
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.9" % Test,
  )

lazy val root = (project in file("."))
  .settings(
    name := "libraryDependenciesOrder"
  )