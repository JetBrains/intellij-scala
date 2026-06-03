scalaVersion := "2.13.14"

lazy val unmanagedDependency = project.in(file(".")).settings(
  libraryDependencies +=  "org.apache.commons" % "commons-compress" % "1.21"
)
