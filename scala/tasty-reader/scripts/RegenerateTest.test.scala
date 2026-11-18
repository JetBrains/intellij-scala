import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.{ZipEntry, ZipOutputStream}

class RegenerateTest extends munit.FunSuite:
  import TastyFixtureRegenerator.{Group, Kind, regenerate}

  private def tasty(suffix: String): Array[Byte] =
    HexFormat.of().parseHex("5ca1ab1f9c80808b") ++
      "Scala 3.0.0".getBytes(UTF_8) ++ suffix.getBytes(UTF_8)

  private def writeJar(path: Path, entry: String, data: Array[Byte]): Unit =
    val archive = new ZipOutputStream(Files.newOutputStream(path))
    try
      archive.putNextEntry(new ZipEntry(entry))
      archive.write(data)
      archive.closeEntry()
    finally archive.close()

  private def sha256(path: Path): String =
    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
    HexFormat.of().formatHex(digest)

  private def deleteTree(root: Path): Unit =
    val paths = Files.walk(root)
    try paths.sorted(java.util.Comparator.reverseOrder[Path]()).forEach(Files.deleteIfExists(_))
    finally paths.close()

  test("a failure in a later group leaves all checked-in fixtures unchanged"):
    val root = Files.createTempDirectory("tasty-fixture-test-")
    try
      val first = root.resolve("libraries/first/A.tasty")
      val second = root.resolve("libraries/second/B.tasty")
      Files.createDirectories(first.getParent)
      Files.createDirectories(second.getParent)
      Files.write(first, tasty("original"))
      Files.write(second, tasty("original"))

      val firstJar = root.resolve("first.jar")
      val secondJar = root.resolve("second.jar")
      writeJar(firstJar, "A.tasty", tasty("replacement"))
      writeJar(secondJar, "B.tasty", tasty("replacement"))

      val groups = Seq(
        Group(Kind.Library, Path.of("libraries/first"), "Scala 3.0.0", "example:first:1", sha256(firstJar)),
        Group(Kind.Library, Path.of("libraries/second"), "Scala 3.0.0", "example:second:1", "0" * 64),
      )
      val jars = Map("example:first:1" -> firstJar, "example:second:1" -> secondJar)

      val error = intercept[IllegalArgumentException]:
        regenerate(root, groups, Map.empty, jars, write = true, allowNew = false)
      assert(error.getMessage.contains("checksum mismatch"), error.getMessage)
      assertEquals(Files.readAllBytes(first).toSeq, tasty("original").toSeq)
      assertEquals(Files.readAllBytes(second).toSeq, tasty("original").toSeq)
    finally deleteTree(root)

  test("dry run leaves fixtures unchanged and regeneration replaces them"):
    val root = Files.createTempDirectory("tasty-fixture-test-")
    try
      val fixture = root.resolve("libraries/example/A.tasty")
      Files.createDirectories(fixture.getParent)
      Files.write(fixture, tasty("original"))
      val jar = root.resolve("example.jar")
      writeJar(jar, "A.tasty", tasty("replacement"))
      val group = Group(Kind.Library, Path.of("libraries/example"), "Scala 3.0.0",
        "example:example:1", sha256(jar))
      val jars = Map(group.origin -> jar)

      regenerate(root, Seq(group), Map.empty, jars, write = false, allowNew = false)
      assertEquals(Files.readAllBytes(fixture).toSeq, tasty("original").toSeq)

      regenerate(root, Seq(group), Map.empty, jars, write = true, allowNew = false)
      assertEquals(Files.readAllBytes(fixture).toSeq, tasty("replacement").toSeq)
    finally deleteTree(root)
