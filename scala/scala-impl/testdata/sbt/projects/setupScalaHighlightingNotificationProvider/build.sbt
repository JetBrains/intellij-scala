lazy val root = project.in(file("."))
  .aggregate(module1, module2)
  .settings(
    name := "setupScalaHighlightingNotificationProvider"
  )

lazy val module1 = project.in(file("module1"))
  .enablePlugins(KotlinPlugin)

lazy val module2 = project.in(file("module2"))
  .dependsOn(module1)
  .settings(
    scalaVersion := "3.6.3"
  )
