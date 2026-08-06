lazy val root = (project in file("."))
  .dependsOn(
    commons1,
    commons2,
    commons3,
    commons4,
  )

lazy val commons1 = RootProject(file("./c1"))
lazy val commons2 = RootProject(file("./c2"))
lazy val commons3 = RootProject(file("./prefix1/prefix2/c3/suffix1/suffix2"))
lazy val commons4 = RootProject(file("./prefix1/prefix2/c4/suffix1/suffix2"))

lazy val project1InRootBuild = project
lazy val project2InRootBuild = project
lazy val project3InRootBuildWithSameName = project.settings(name := "same name in root build")
lazy val project4InRootBuildWithSameName = project.settings(name := "same name in root build")
lazy val project5InRootBuildWithSameGlobalName = project.settings(name := "same global name")
lazy val project6InRootBuildWithSameGlobalName = project.settings(name := "same global name")