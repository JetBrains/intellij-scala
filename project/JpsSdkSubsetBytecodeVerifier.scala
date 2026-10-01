import java.io.{DataInputStream, File}
import java.nio.file.{FileSystems, Files, Path}
import scala.collection.JavaConverters.*

/**
 * Verifies that the jars referenced by [[IntellijSdkSubsetInfo]] subsets contain only
 * bytecode that can be executed on specific Java versions (varies by subset).
 *
 * Code from these subsets is executed outside the IDE (the JPS build process, the compile
 * server), where the JVM may be older than the one running the IDE. Shipping bytecode that
 * requires a newer Java than the JPS runtime would fail at runtime even though it compiled
 * fine. This check guards against that and against the hardcoded jar paths going stale.
 *
 * Part of SCL-25518.
 */
object JpsSdkSubsetBytecodeVerifier {

  /**
   * Class-file major version corresponding to a Java feature version.
   * Java 1 == 45, and every feature version since adds 1 (Java 8 == 52, Java 11 == 55, Java 12 == 56).
   */
  private final val ClassMajorVersionOffset = 44

  private final val ClassFileMagic = 0xCAFEBABE

  private def maxAllowedClassMajorVersion(version: Int) = version + ClassMajorVersionOffset

  private def javaVersionOfClassMajor(major: Int): Int = major - ClassMajorVersionOffset

  /** A class file whose bytecode requires a newer Java than [[IntellijSdkSubsetInfo.maxAllowedJavaVersion]]. */
  final case class Violation(jar: File, entry: String, classMajorVersion: Int, maxAllowedJavaVersion: Int) {
    def requiredJavaVersion: Int = javaVersionOfClassMajor(classMajorVersion)
  }

  /** A jar referenced by a subset that does not exist on disk (the hardcoded path is stale). */
  final case class MissingJar(subsetName: String, relativePath: String, file: File)

  final case class Result(
    violations: Seq[Violation],
    missingJars: Seq[MissingJar],
    scannedJars: Int,
    scannedClasses: Int,
  ) {
    def hasProblems: Boolean = violations.nonEmpty || missingJars.nonEmpty
  }

  def verify(
    subsets: Seq[IntellijSdkSubsetInfo],
    buildNumber: String,
    intellijBaseDir: File,
  ): Result = {
    // Pair every referenced jar with the subset, relative path it came from (for diagnostics) and the
    // maximum allowed Java version for that subset.
    val referencedJars: Seq[(String, String, File, Int)] = subsets.flatMap { subset =>
      val materialised = subset.toMaterialisedInfo(buildNumber, intellijBaseDir)
      subset.jarsRelativePaths.zip(materialised.jarFiles).map { case (relativePath, jarFile) =>
        (subset.artifact.name, relativePath, jarFile, subset.maxAllowedJavaVersion)
      }
    }

    val missingJars = referencedJars.collect {
      case (subsetName, relativePath, jarFile, _) if !jarFile.exists() =>
        MissingJar(subsetName, relativePath, jarFile)
    }

    // Dedup existing jars by canonical path, keeping the strictest limit: e.g. util-8.jar is referenced by
    // several subsets with different limits. A class that breaks a looser limit also breaks the strictest one.
    val existingJars: Seq[(File, Int)] = referencedJars
      .collect { case (_, _, jarFile, maxAllowedJavaVersion) if jarFile.exists() => (jarFile, maxAllowedJavaVersion) }
      .map { case (file, version) => (file.getCanonicalFile, version) }
      .groupBy(_._1)
      .map { case (file, pairs) => (file, pairs.map(_._2).min) }
      .toSeq
      .sortBy(_._1.getPath)

    var scannedClasses = 0
    val violations = existingJars.flatMap { case (jarFile, maxAllowedJavaVersion) =>
      val (jarViolations, classCount) = scanJar(jarFile, maxAllowedJavaVersion)
      scannedClasses += classCount
      jarViolations
    }

    Result(violations, missingJars, existingJars.size, scannedClasses)
  }

  private def scanJar(jarFile: File, version: Int): (Seq[Violation], Int) = {
    val violations = Seq.newBuilder[Violation]
    var classCount = 0

    // Open the jar as a java.nio filesystem. Jars are deduped before this point, so a
    // FileSystemAlreadyExistsException would signal a real bug and is intentionally not caught.
    val fileSystem = FileSystems.newFileSystem(jarFile.toPath)
    try {
      val paths = Files.walk(fileSystem.getPath("/"))
      try {
        paths.iterator().asScala.foreach { path =>
          val entry = path.toString
          // Multi-release versioned entries (META-INF/versions/<N>/...) are only loaded on a Java <N>+
          // runtime, so they do not affect whether the jar runs on an older Java; skip them.
          if (entry.endsWith(".class") && !entry.startsWith("/META-INF/versions/") && Files.isRegularFile(path)) {
            classCount += 1
            val major = readClassMajorVersion(path)
            if (major > maxAllowedClassMajorVersion(version))
              violations += Violation(jarFile, entry, major, version)
          }
        }
      } finally paths.close()
    } finally fileSystem.close()

    (violations.result(), classCount)
  }

  private def readClassMajorVersion(classFile: Path): Int = {
    val in = new DataInputStream(Files.newInputStream(classFile))
    try {
      val magic = in.readInt()
      if (magic != ClassFileMagic)
        sys.error(s"Not a valid class file (unexpected magic 0x${magic.toHexString}): $classFile")
      in.readUnsignedShort() // minor version (ignored)
      in.readUnsignedShort() // major version
    } finally in.close()
  }
}
