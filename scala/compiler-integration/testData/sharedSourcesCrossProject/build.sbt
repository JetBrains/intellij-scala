lazy val root = project.in(file("."))

lazy val middle = crossProject(JVMPlatform, JSPlatform).in(file("middle"))

lazy val base = crossProject(JVMPlatform, JSPlatform).in(file("base"))
  .dependsOn(middle)
