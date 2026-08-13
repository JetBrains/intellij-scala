lazy val root = (project in file("."))
  .settings(
    name := "my-test-betterMonadicFor",
    scalaVersion := "2.13.18",
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
