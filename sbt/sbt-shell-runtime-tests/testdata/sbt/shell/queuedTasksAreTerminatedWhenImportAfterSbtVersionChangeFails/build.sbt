scalaVersion := "2.13.18"

val task = taskKey[Unit]("task")

lazy val root = (project in file("."))
  .settings(
    task := {
      Thread.sleep(3000)
    }
  )
