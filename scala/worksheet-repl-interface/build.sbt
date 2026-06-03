// This build requires JDK 17.
val jdk17 = {
  System.getProperty("java.version") match {
    case version if version.startsWith("17.") => 17
    case _ => sys.error("JDK 17 is required for this build. Use `sbt --java-home <path to JDK 17>` to run sbt with a specific JDK 17 installation.")
  }
}

lazy val cleanAll: TaskKey[Unit] = taskKey("Run clean in all subprojects")

ThisBuild / cleanAll := {
  clean.all(ScopeFilter(projects = inAnyProject, configurations = inAnyConfiguration)).value
}

lazy val projectDirectoriesSettings: Seq[Setting[?]] = Seq(
  // production sources
  Compile / sourceDirectory := baseDirectory.value / "src", // we put all source files in <subproject_dir>/src
  Compile / unmanagedSourceDirectories := Seq((Compile / sourceDirectory).value),
)

lazy val packageCommonSettings: Seq[Setting[?]] = Seq(
  Compile / packageBin / artifactName := { (_, _, artifact) => s"${artifact.name}.${artifact.extension}" },
  Compile / packageSrc / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false
)

val interfaceVersion = "1.0.0"
val implsVersion = "1.0.0"

lazy val worksheetReplInterface =
  Project("worksheet-repl-interface", file("."))
    .settings(
      name := "worksheet-repl-interface",
      crossPaths := false,
      autoScalaLibrary := false
    )
    .settings(projectDirectoriesSettings)
    .aggregate(replInterface, impls)

lazy val replInterface =
  Project("repl-interface", file("repl-interface"))
    .settings(
      name := "repl-interface",
      organization := "JetBrains",
      version := interfaceVersion,
      crossPaths := false, // disable using the Scala version in output paths and artifacts
      autoScalaLibrary := false, // removes Scala dependency
      Compile / javacOptions := Seq("--release", "8"), // can run in the compile server
      Compile / scalacOptions := Seq.empty // scala is disabled anyway, set empty options to move to a separate compiler profile (in IntelliJ model)
    )
    .settings(projectDirectoriesSettings)
    .settings(packageCommonSettings)

lazy val impls =
  project.in(file("impls"))
    .settings(
      name := "impls",
      organization := "JetBrains",
      version := implsVersion,
      crossPaths := false,
      autoScalaLibrary := false,
      Compile / packageBin / mappings := {
        val classDirectories = classDirectory.all(
          ScopeFilter(
            projects = inDependencies(ThisProject, transitive = false, includeRoot = false),
            configurations = inConfigurations(Compile)
          )
        ).value
        import Path.relativeTo
        classDirectories.flatMap { dir =>
          val classes = (dir ** ("*.class" || "*.tasty")).get()
          classes.pair(relativeTo(Seq(dir)))
        }
      },
      Compile / packageBin / mappings := (Compile / packageBin / mappings).dependsOn(Compile / compile).value
    )
    .settings(packageCommonSettings)
    .dependsOn(
      worksheetReplInterfaceImpl_2_12,
      worksheetReplInterfaceImpl_2_12_13,
      worksheetReplInterfaceImpl_2_13_0,
      worksheetReplInterfaceImpl_2_13,
      worksheetReplInterfaceImpl_2_13_12,
      worksheetReplInterfaceImpl_3_0_0,
      worksheetReplInterfaceImpl_3_1_2,
      worksheetReplInterfaceImpl_3_3_0,
      worksheetReplInterfaceImpl_3_8
    )

def worksheetReplInterfaceImplCommonSettings(scalaVer: String): Seq[Setting[?]] = Seq(
  version := implsVersion,
  scalaVersion := scalaVer,
  crossPaths := false,
  libraryDependencies += {
    if (scalaVer.startsWith("3."))
      "org.scala-lang" %% "scala3-compiler" % scalaVer % Provided
    else
      "org.scala-lang" % "scala-compiler" % scalaVer % Provided
  },
  Compile / javacOptions := Seq("--release", "8"),
  Compile / scalacOptions := Seq("-release", "8") // not all Scala versions used in this project support the Unix-like `--release` flag
) ++ projectDirectoriesSettings

lazy val worksheetReplInterfaceImpl_2_12: Project =
  Project("worksheet-repl-interface-impl_2_12", file("impls/impl_2_12"))
    .dependsOn(replInterface)
    .settings(
      worksheetReplInterfaceImplCommonSettings("2.12.12"),
      Compile / scalacOptions := Seq("-target:jvm-1.8") // Old version of Scala 2.12 does not have the modern compiler flags
    )
    .settings(
      libraryDependencies ++= Seq(
        compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
        "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
      ),
      Compile / scalacOptions += "-deprecation",
      // This is a workaround for manually enabling the `silencer-plugin` scalac compiler plugin. For some reason,
      // automatic enabling doesn't work (the scalacOption "-Xplugin:" was not added).
      // The silencer plugin is needed because this subproject is compiled using Scala 2.12.12 which did not have
      // support for `@scala.annotation.nowarn`.
      autoCompilerPlugins := false,
      ivyConfigurations += Configurations.CompilerPlugin,
      Compile / scalacOptions ++= Classpaths.autoPlugins(update.value, Seq.empty, isDotty = false)
    )

lazy val worksheetReplInterfaceImpl_2_12_13: Project =
  Project("worksheet-repl-interface-impl_2_12_13", file("impls/impl_2_12_13"))
    .dependsOn(replInterface)
    .settings(
      worksheetReplInterfaceImplCommonSettings("2.12.18"),
      Compile / scalacOptions += "-deprecation"
    )

lazy val worksheetReplInterfaceImpl_2_13_0: Project =
  Project("worksheet-repl-interface-impl_2_13_0", file("impls/impl_2_13_0"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.0"))

lazy val worksheetReplInterfaceImpl_2_13: Project =
  Project("worksheet-repl-interface-impl_2_13", file("impls/impl_2_13"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.11"))

lazy val worksheetReplInterfaceImpl_2_13_12: Project =
  Project("worksheet-repl-interface-impl_2_13_12", file("impls/impl_2_13_12"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("2.13.12"))

lazy val worksheetReplInterfaceImpl_3_0_0: Project =
  Project("worksheet-repl-interface-impl_3_0_0", file("impls/impl_3_0_0"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.1.1"))

lazy val worksheetReplInterfaceImpl_3_1_2: Project =
  Project("worksheet-repl-interface-impl_3_1_2", file("impls/impl_3_1_2"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.2.2"))

lazy val worksheetReplInterfaceImpl_3_3_0: Project =
  Project("worksheet-repl-interface-impl_3_3_0", file("impls/impl_3_3_0"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.3.1"))

lazy val worksheetReplInterfaceImpl_3_8: Project =
  Project("worksheet-repl-interface-impl_3_8", file("impls/impl_3_8"))
    .dependsOn(replInterface)
    .settings(worksheetReplInterfaceImplCommonSettings("3.8.0-RC1"))
    .settings(
      libraryDependencies += "org.scala-lang" %% "scala3-repl" % scalaVersion.value,
      Compile / scalacOptions := Seq("--release", "17")
    )
