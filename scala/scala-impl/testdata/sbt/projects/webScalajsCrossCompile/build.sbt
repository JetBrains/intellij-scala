ThisBuild / scalaVersion := "2.13.16"

lazy val root = project.in(file(".")).
  aggregate(barJS, barJVM).
  settings(
    publish := {},
    publishLocal := {},
  )

lazy val foo = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).in(file("foo")).
  settings(
    name := "foo",
    version := "0.1-SNAPSHOT",
  ).
  jvmSettings(
    // Add JVM-specific settings here
  ).
  jsSettings(
    // Add JS-specific settings here
  )

lazy val barJS = (project in file("bar/js"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  )
  .dependsOn(foo.js)

lazy val barJVM = (project in file("bar/jvm"))
  .enablePlugins(WebScalaJSBundlerPlugin)
  .settings(
    scalaJSProjects := Seq(barJS),
  )
  .dependsOn(foo.jvm)
