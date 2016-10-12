object Bar

organization

"some string"

name := "someName"
version := "SNAPSHOT"

enablePlugins(sbt.plugins.JvmPlugin)

lazy val foo = project.in(file("foo")).enablePlugins(sbt.plugins.JvmPlugin)

null

???

Seq(
  name := "OK",
  version := "0.0.0"
)