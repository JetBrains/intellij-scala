lazy val root = project.in(file(".")).settings(name := "Build C1 Name")

lazy val project1InC1 = project
lazy val project2InC1 = project
lazy val project3InC1WithSameName = project.settings(name := "same name in c1")
lazy val project4InC1WithSameName = project.settings(name := "same name in c1")
lazy val project5InC1WithSameGlobalName = project.settings(name := "same global name")
lazy val project6InC1WithSameGlobalName = project.settings(name := "same global name")