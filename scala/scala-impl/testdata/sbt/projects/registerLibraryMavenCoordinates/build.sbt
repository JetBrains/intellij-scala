val scala2Version = "2.13.14"
val scala3Version = "3.3.3"

ThisBuild / scalaVersion := scala2Version

lazy val root = (project in file(".")).settings(name := "root")

lazy val withScala33 = (project in file("withScala33"))
  .settings(
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      ("org.scalameta" %% "munit" % "1.2.1").intransitive(),
      ("junit" % "junit" % "4.13.2").intransitive(),
    )
  )

lazy val withScala213 = (project in file("withScala213"))
  .settings(
    scalaVersion := scala2Version,
    libraryDependencies ++= Seq(
      ("org.typelevel" %% "cats-core" % "2.13.0").intransitive(),
    )
  )