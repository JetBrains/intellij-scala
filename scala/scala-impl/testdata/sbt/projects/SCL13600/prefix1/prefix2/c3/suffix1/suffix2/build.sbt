lazy val root = project.in(file("."))

lazy val project1InC3 = project
lazy val project2InC3 = project
lazy val project3InC3WithSameName = project.settings(name := "same name in c3")
lazy val project4InC3WithSameName = project.settings(name := "same name in c3")
lazy val project5InC3WithSameGlobalName = project.settings(name := "same global name")
lazy val project6InC3WithSameGlobalName = project.settings(name := "same global name")