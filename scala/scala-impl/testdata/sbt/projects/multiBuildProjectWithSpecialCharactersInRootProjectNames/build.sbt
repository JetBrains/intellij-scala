lazy val root = (project in file("."))
  .settings(name := "ro//o.t.")
  .dependsOn(
    c1
  )

lazy val c1 = RootProject(file("./c1"))
lazy val project1 = project.in(file("project"))

