//import Dependencies._ //.inaccessible

val subProjectSeparateRoot = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(),
    name := "sub-project-separate",
  )

def dummyMethodToTriggerInspection() {}
//unresolvedReference