ThisBuild / scalaVersion := "2.13.16"

lazy val root = project.in(file("."))
  .aggregate(p1.js, p1.jvm, p1.native)
  .settings(
    name := "root"
  )

lazy val p1 = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("p1"))
  .jvmSettings(
    Compile / unmanagedSourceDirectories ++= Seq(
      ((ThisBuild / baseDirectory).value / "p1_jvm_src_external").getAbsoluteFile
    )
  )
  .jsSettings(
    Test / unmanagedSourceDirectories ++= Seq(
      ((ThisBuild / baseDirectory).value / "p1_js_src_external").getAbsoluteFile
    )
  )
  .nativeSettings(
    Test / unmanagedSourceDirectories ++= Seq(
      ((ThisBuild / baseDirectory).value / "p1_native_src_external").getAbsoluteFile
    )
  )
