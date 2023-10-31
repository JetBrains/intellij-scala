ThisBuild / scalaVersion := "2.13.11"
name := "customConfigurationsWithNestedProjectDependencies"

lazy val CustomTest = config("customtest").extend(Test)
lazy val CustomCompile = config("customcompile").extend(Compile)

lazy val root = (project in file("."))

lazy val foo = (project in file("foo"))
  .configs(CustomTest)
  .settings(
    inConfig(CustomTest)(Defaults.configSettings),
  )
  .dependsOn(root % "customtest->compile")

lazy val utils = (project in file("utils"))
  .configs(CustomTest, CustomCompile)
  .settings(
    inConfig(CustomCompile)(Defaults.configSettings),
    inConfig(CustomTest)(Defaults.configSettings),
  )
  .dependsOn(foo % "customtest->customtest;customcompile")
