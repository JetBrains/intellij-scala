LocalRootProject / name := "sbt-projectmatrix-sbt2-js-native"
LocalRootProject / scalaVersion := "2.12.17"

// Match Scala.js 1.22.0's Scala libraries; sbt 2 enforces SIP-51 for Scala 2.13.
val scalaVersions = Seq("2.12.21", "2.13.18")

lazy val upstream = (projectMatrix in file("upstream"))
  .settings(commonSettings("upstream") *)
  .jsPlatform(scalaVersions = scalaVersions)
  .nativePlatform(scalaVersions = scalaVersions)

lazy val downstream = (projectMatrix in file("downstream"))
  .settings(commonSettings("downstream") *)
  .dependsOn(upstream)
  .jsPlatform(scalaVersions = scalaVersions)
  .nativePlatform(scalaVersions = scalaVersions)

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
