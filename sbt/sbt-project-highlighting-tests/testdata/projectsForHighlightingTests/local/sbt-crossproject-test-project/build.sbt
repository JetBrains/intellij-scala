//https://github.com/portable-scala/sbt-crossproject
ThisBuild / scalaVersion := "2.13.10"

ThisBuild / name := "sbt-crossproject-test-project"

lazy val coreFull =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Full)
    .settings(scalaVersion := "2.13.10")
    //.jvmSettings(/* ... */)
    //.jsSettings(/* ... defined in sbt-scalajs-crossproject*/)
    //.nativeSettings(/* ... defined in sbt-scala-native */)

lazy val coreFullWithUnmanagedLibraries =
  crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Full)
    .settings(
      scalaVersion := "2.13.10",
      exportJars := true
    )

lazy val corePure =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .settings(scalaVersion := "2.13.10")

// Optional in sbt 1.x (mandatory in sbt 0.13.x)
//lazy val coreJS     = core.js
//lazy val coreJVM    = core.jvm
//lazy val coreNative = core.native