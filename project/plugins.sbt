ThisBuild / resolvers += "JetBrains Maven Central" at "https://cache-redirector.jetbrains.com/maven-central"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

//NOTE: KEEP VERSIONS IN SYNC WITH ultimate/project/plugins.sbt
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.4")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.19.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-idea-plugin" % "6.0.0-RC3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")
addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % "4.0.0")

// Only used for local development purposes, not in CI/CD.
// See ../README.md for some examples of how to generate reports locally.
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "2.4.4")

libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier-sbt-maven-repository" % "2.1.24",
  "com.lihaoyi" %% "upickle" % "4.4.3",
)
