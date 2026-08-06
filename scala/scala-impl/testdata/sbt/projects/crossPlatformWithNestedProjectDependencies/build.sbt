ThisBuild / scalaVersion := "2.11.12"
name := "crossPlatformWithNestedProjectDependencies"

lazy val module1 = crossProject(JVMPlatform, JSPlatform)
  .build()

lazy val module2 = crossProject(JVMPlatform, JSPlatform)
  .dependsOn(module1 % "test")

lazy val module3 = (project in file("module3"))
  .dependsOn(module2.jvm % "test->test")

lazy val root = (project in file("."))
  .dependsOn(module2.jvm)
