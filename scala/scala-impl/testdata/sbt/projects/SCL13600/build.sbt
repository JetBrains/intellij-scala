
lazy val root = (project in file("."))
  .dependsOn(commons, another_commons)

lazy val commons = RootProject(file("./c1"))
lazy val another_commons = RootProject(file("./c2"))
