name := "java-language-level-and-target-byte-code-level-no-options"
version := "0.1"
scalaVersion := "2.13.4"

javacOptions := Seq()

val module1   = project.settings(javacOptions := Seq())