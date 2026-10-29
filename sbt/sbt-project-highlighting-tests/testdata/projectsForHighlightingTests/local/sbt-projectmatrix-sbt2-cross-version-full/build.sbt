LocalRootProject / name := "sbt-projectmatrix-sbt2-cross-version-full"
LocalRootProject / scalaVersion := "2.12.17"

val scalaVersions = Seq("2.13.10", "2.13.18")

// Plain val and two patches of one binary version exercise sbt #8421's full-version axes.
val foo = projectMatrix
  .settings(commonSettings("foo") *)
  .jvmPlatform(crossVersion = CrossVersion.full, scalaVersions = scalaVersions)

// Reordered arguments with an inline Seq cover sbt #8458/#8484's synthetic-ID regression.
// See the introducing commit's message for context and upstream links.
val reordered = projectMatrix
  .settings(commonSettings("reordered") *)
  .jvmPlatform(scalaVersions = Seq("2.13.10", "2.13.18"), crossVersion = CrossVersion.full)

def generateSource(matrixName: String, scope: String, directory: File): Seq[File] = {
  val className = if (scope == "main") "GeneratedMain" else "GeneratedTest"
  val output = new File(directory, s"example/$matrixName/$className.scala")
  IO.write(output, s"""package example.$matrixName
                        |object $className { val value: String = "$scope-generated" }
                        |""".stripMargin)
  Seq(output)
}

def commonSettings(matrixName: String): Seq[Def.Setting[?]] = Seq(
  // Materialize both generated roots during import, as in the existing matrix fixtures.
  update := Def.task {
    (Compile / managedSources).value
    (Test / managedSources).value
    update.value
  }.value,
  Compile / sourceGenerators += Def.task {
    generateSource(matrixName, "main", (Compile / sourceManaged).value)
  }.taskValue,
  Test / sourceGenerators += Def.task {
    generateSource(matrixName, "test", (Test / sourceManaged).value)
  }.taskValue,
)
