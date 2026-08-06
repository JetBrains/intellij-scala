ThisBuild / scalaVersion := "2.13.14"

shellPrompt := { _ => "customPrompt" }
lazy val root = project.in (file ("."))
