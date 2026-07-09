ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(name := "close-during-import-sbt")


Global / onLoad := {
  val previous = (Global / onLoad).value
  state =>
    coordinateCloseDuringImportTest()
    // After the test-specific synchronization point is handled, continue with sbt's normal onLoad chain. The fixture
    // should remain a real sbt project; this hook only controls timing and must not replace standard load behavior.
    previous(state)
}

def coordinateCloseDuringImportTest(): Unit = {
  // The IDEA test coordinates with this copied temporary workspace through marker files.
  // This keeps the fixture on the real sbt BSP path while still giving the test deterministic synchronization points
  // around connection-file generation and server startup.
  val base = new java.io.File(sys.props("user.dir"))
  val skipLoadMarker = new java.io.File(base, "skip-sbt-build-load-once.marker")
  val skipConsumedMarker = new java.io.File(base, "sbt-build-load-skip-consumed.txt")
  val loadMarker = new java.io.File(base, "sbt-build-load.marker")
  val loadCountFile = new java.io.File(base, "sbt-build-load-count.txt")
  val releaseMarker = new java.io.File(base, "release-sbt-build-load.marker")

  // Record every sbt build load observed by this hook.
  // The test expects two loads:
  //   1. one fast load for generating `.bsp/sbt.json`,
  //   2. and one blocked load for the actual BSP import.
  // If sbt/IDEA load ordering changes, this count gives a direct diagnostic
  // instead of leaving the test to fail later with an ambiguous timeout.
  val loadCountText: String =
    if (loadCountFile.isFile)
      new String(java.nio.file.Files.readAllBytes(loadCountFile.toPath), java.nio.charset.StandardCharsets.UTF_8).trim
    else
      "0"
  val loadCount: Int =
    if (loadCountText.isEmpty) 0
    else try loadCountText.toInt catch { case _: NumberFormatException => 0 }

  val loadCountNew = loadCount + 1
  java.nio.file.Files.writeString(
    loadCountFile.toPath,
    loadCountNew.toString,
    java.nio.charset.StandardCharsets.UTF_8
  )

  // The first load consumes a one-shot skip marker and records which load number consumed it. BspOpenProjectProvider
  // must be allowed to run the production sbt setup and generate `.bsp/sbt.json` quickly; blocking this first load
  // would test the setup step rather than close-during-import cancellation.
  if (java.nio.file.Files.deleteIfExists(skipLoadMarker.toPath)) {
    java.nio.file.Files.writeString(
      skipConsumedMarker.toPath,
      loadCountNew.toString,
      java.nio.charset.StandardCharsets.UTF_8
    )
  } else {
    // Later loads signal that real BSP import reached sbt build loading, then wait for the release marker.
    // This keeps the BSP server busy while the IDEA test closes the project. The short sleeps make the wait
    // responsive to cleanup, while the deadline prevents a broken test run from leaving sbt blocked indefinitely.
    loadMarker.createNewFile()

    val deadline = System.currentTimeMillis() + 60000
    while (!releaseMarker.exists() && System.currentTimeMillis() < deadline) {
      Thread.sleep(100)
    }
  }
}