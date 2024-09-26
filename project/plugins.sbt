ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

//NOTE: KEEP VERSIONS IN SYNC WITH ultimate/project/plugins.sbt
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "4.0.0-RC17")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "3.0.3")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.13.0")

// Only used for local development purposes, not in CI/CD.
// Should not be present in the scala-plugin-for-ultimate repos.
// See ../README.md for some examples of how to generate reports locally.
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "2.0.12")

libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier-sbt-maven-repository" % "2.1.10"
)
