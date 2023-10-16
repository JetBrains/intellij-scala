lazy val root = project.in(file(".")).settings(name := "Build C2 Name")

lazy val project1InC2 = project
lazy val project2InC2 = project
lazy val project3InC2WithSameName = project.settings(name := "same name in c2")
lazy val project4InC2WithSameName = project.settings(name := "same name in c2")
lazy val project5InC2WithSameGlobalName = project.settings(name := "same global name")
lazy val project6InC2WithSameGlobalName = project.settings(name := "same global name")