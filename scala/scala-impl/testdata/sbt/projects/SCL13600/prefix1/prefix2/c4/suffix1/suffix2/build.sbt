lazy val root = project.in(file("."))

lazy val project1InC4 = project
lazy val project2InC4 = project
lazy val project3InC4WithSameName = project.settings(name := "same name in c4")
lazy val project4InC4WithSameName = project.settings(name := "same name in c4")
lazy val project5InC4WithSameGlobalName = project.settings(name := "same global name")
lazy val project6InC4WithSameGlobalName = project.settings(name := "same global name")