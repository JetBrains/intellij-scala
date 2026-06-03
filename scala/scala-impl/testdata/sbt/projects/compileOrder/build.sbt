ThisBuild / scalaVersion := "2.13.10"

lazy val unspecified = project.in(file("."))
  .aggregate(mixed, scalaThenJava, javaThenScala)
  .settings(
    name := "compile-order-unspecified"
  )

lazy val mixed = project.in(file("mixed"))
  .settings(
    name := "compile-order-mixed",
    compileOrder := CompileOrder.Mixed
  )

lazy val scalaThenJava = project.in(file("scalaThenJava"))
  .settings(
    name := "compile-order-scala-then-java",
    compileOrder := CompileOrder.ScalaThenJava
  )

lazy val javaThenScala = project.in(file("javaThenScala"))
  .settings(
    name := "compile-order-java-then-scala",
    compileOrder := CompileOrder.JavaThenScala
  )
