ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "SimpleProjectWithGeneratedSources",
    Compile / managedSourceDirectories ++= Seq(baseDirectory.value / "target" / "myGenerated" / "main"),
    Test / managedSourceDirectories ++= Seq(baseDirectory.value / "target" / "myGenerated" / "test"),
  )