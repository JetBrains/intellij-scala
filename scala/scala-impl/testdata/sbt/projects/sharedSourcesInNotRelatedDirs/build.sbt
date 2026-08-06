import sbt.Compile

ThisBuild / scalaVersion := "3.0.2"

lazy val foo = (project in file("foo"))
  .settings(
    Test / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value  / "dummy" / "src" ,
    Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "nothing" /"dummy"
  )

lazy val buzz = (project in file("buzz"))
  .settings(
    Test / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value  / "dummy" / "src",
    Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "nothing" /"dummy" ,
  )

lazy val root = (project in file("."))
  .dependsOn(buzz % "test->test")
