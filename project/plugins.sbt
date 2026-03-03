ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

//NOTE: KEEP VERSIONS IN SYNC WITH ultimate/project/plugins.sbt
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.4")
addSbtPlugin("org.jetbrains.scala" % "sbt-idea-plugin" % "5.1.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "3.1.7")

// Only used for local development purposes, not in CI/CD.
// See ../README.md for some examples of how to generate reports locally.
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "2.4.4")

libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier-sbt-maven-repository" % "2.1.24"
)
