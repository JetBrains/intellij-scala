lazy val root = (project in file("."))
  .dependsOn(c1)

lazy val c1 = RootProject(file("./c1"))
val dummy = project.in(file("dummy"))

