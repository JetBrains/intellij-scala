import kotlin.Keys._

ThisBuild / scalaVersion := "2.13.14"

lazy val root = project.in(file("."))
  .aggregate(
    `module-with-enabled-plugin-and-kotlinc-options`,
    `module-with-enabled-plugin-and-no-kotlinc-options`,
    `module-with-disabled-plugin`
  )
  .settings(
    name := "kotlincOptionsFromSbtKotlinPluginPerModule",
  )

lazy val `module-with-enabled-plugin-and-kotlinc-options` =
  project.in(file("module-with-enabled-plugin-and-kotlinc-options"))
    .enablePlugins(KotlinPlugin)
    .settings(
      kotlinVersion := "2.3.0",
      kotlinRuntimeProvided := true,
      kotlincJvmTarget := "17",
      kotlincOptions ++= Seq(
        "-Xjsr305=strict",
        "-progressive",
        "-opt-in=kotlin.RequiresOptIn",
        "-nowarn",
      ),
    )

lazy val `module-with-enabled-plugin-and-no-kotlinc-options` =
  project.in(file("module-with-enabled-plugin-and-no-kotlinc-options"))
    .enablePlugins(KotlinPlugin)
    .settings(
      kotlinVersion := "2.3.0",
      kotlinRuntimeProvided := true,
      kotlincJvmTarget := "17",
    )

lazy val `module-with-disabled-plugin` =
  project.in(file("module-with-disabled-plugin"))
