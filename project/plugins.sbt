//NOTE: KEEP VERSIONS IN SYNC WITH ultimate/project/plugins.sbt
addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.0.0")
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "3.14.9")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")

// Only used for local development purposes, not in CI/CD.
// Should not be present in the scala-plugin-for-ultimate repos.
// See ../README.md for some examples of how to generate reports locally.
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.9.3")

libraryDependencies += "io.get-coursier" %% "coursier" % "2.0.16"
