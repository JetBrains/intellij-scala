lazy val root = (project in file("."))
  .settings(
    name := "nested-source-directories-deduplication",
    scalaVersion := "3.7.4",
    Compile / unmanagedSourceDirectories += (Compile / sourceDirectory).value / "scala" / "nested"
  )
