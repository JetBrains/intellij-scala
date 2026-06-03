ThisBuild / scalaVersion := "3.3.1"

lazy val separateCompilerOutputPaths = project.in(file(".")).aggregate(module1, module2, module3)

lazy val module1 = project.in(file("module1"))

lazy val module2 = project.in(file("module2")).dependsOn(module1)

lazy val module3 = project.in(file("module3")).dependsOn(module2)
