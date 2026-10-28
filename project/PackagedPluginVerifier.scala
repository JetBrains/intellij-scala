import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipFile}
import scala.collection.JavaConverters.*
import scala.collection.mutable

/**
 * Verifies the assembled plugin (the output of `packageArtifact`) against packaging problems that are easy to
 * introduce by changing one place and not the others: the Zinc assembly exclusions in `Dependencies.zinc`, the
 * library mappings of the compile server projects, the `compileServer.plugin` classpath (the JPS build process)
 * in `scalaCommunity.scala-impl.xml` and the jars shipped in `lib/jps`.
 *
 *  - No class is packaged in more than one jar under `lib/`, except for the documented intentional duplicates.
 *  - Every entry of the `compileServer.plugin` classpath exists.
 *  - Every jar in `lib/jps` is either on that classpath or one of the per Scala version artifacts.
 *  - A marker class of every library the JPS process needs is present on its classpath, exactly once.
 *
 * Run via the `verifyPackagedPlugin` task after `packageArtifact`. Only the jar indices are read, the whole check
 * takes a fraction of a second.
 *
 * The compile server process classpath ([[org.jetbrains.plugins.scala.compiler.CompileServerLauncher.compileServerJars]])
 * is code and is checked against the same rules by `CompileServerClasspathTest`.
 */
object PackagedPluginVerifier {

  /**
   * Jars that are allowed to contain the same classes. A duplicated class is tolerated only when every jar
   * containing it matches the same group.
   */
  private val AllowedDuplicateGroups: Seq[String => Boolean] = Seq(
    // The Scala Compile Server can run on Java 8 and ships its own standard library (lib/scala-library.jar is Java 17 bytecode).
    Set("scala-library.jar", "jps/compile-server-scala-library.jar", "jps/compile-server-scala3-library_3.jar"),
    // Per Scala version artifacts, only one of each family is loaded at a time.
    _.matches("""jps/scala3-sbt-bridge_.*\.jar"""),
    _.matches("""jps/compiler-plugin-.*\.jar"""),
  )

  /** Jars in `lib/jps` that are not on the JPS classpath: the compile server picks one per Scala version at runtime. */
  private val PerScalaVersionJpsJar = """jps/(compiler-bridge-sources_|scala3-sbt-bridge_|compiler-plugin-).*\.jar""".r

  /**
   * One class per library the JPS process needs. Guards against a library being excluded from the Zinc assembly
   * without being shipped separately, and against a jar being dropped from the classpath.
   */
  private val JpsClasspathMarkerClasses: Seq[String] = Seq(
    "scala/Option.class",                                                   // compile-server-scala-library.jar
    "scala/Tuple.class",                                                    // compile-server-scala3-library_3.jar
    "scala/xml/Node.class",                                                 // scala-xml.jar
    "scala/util/parsing/combinator/RegexParsers.class",                     // scala-parser-combinators.jar
    "scala/collection/parallel/ParIterable.class",                          // incremental-compiler.jar, Zinc needs it
    "sbt/internal/inc/IncrementalCompilerImpl.class",                       // incremental-compiler.jar
    "xsbti/Logger.class",                                                   // sbt-interface.jar
    "xsbti/compile/CompileAnalysis.class",                                  // compiler-interface.jar
    "com/facebook/nailgun/NGServer.class",                                  // nailgun.jar
    "spray/json/JsonFormat.class",                                          // spray-json
    "org/jetbrains/plugins/scala/indices/protocol/CompilationInfo.class",   // scala-compiler-indices-protocol
    "org/jetbrains/plugins/scala/compiler/data/Arguments.class",            // compiler-shared.jar
    "org/jetbrains/plugins/scala/compiler/references/Messages.class",       // scala-jps.jar
    "org/jetbrains/plugins/scala/util/JarManifestUtils.class",              // utils_rt.jar
    "org/jetbrains/jps/incremental/scala/remote/Main.class",                // compile-server.jar
    "org/jetbrains/jps/incremental/scala/ScalaBuilder.class",               // compiler-jps.jar
  )

  private val CompileServerPluginClasspath = """<compileServer\.plugin\s+classpath="([^"]+)"""".r

  /** Classes packaged in every jar of `jars` (paths relative to `lib/`). */
  final case class DuplicatedClasses(jars: Seq[String], classes: Seq[String])

  /** A marker class that is not present exactly once on the JPS classpath. */
  final case class MarkerProblem(className: String, foundIn: Seq[String])

  final case class Result(
    jpsClasspath: Seq[String],
    duplicates: Seq[DuplicatedClasses],
    missingClasspathEntries: Seq[String],
    unreferencedJpsJars: Seq[String],
    markerProblems: Seq[MarkerProblem],
    scannedJars: Int,
    scannedClasses: Int,
  ) {
    def hasProblems: Boolean =
      jpsClasspath.isEmpty || duplicates.nonEmpty || missingClasspathEntries.nonEmpty ||
        unreferencedJpsJars.nonEmpty || markerProblems.nonEmpty

    def problemSections: Seq[String] = {
      def section(title: String, items: Seq[String]): Option[String] =
        if (items.isEmpty) None else Some((title +: items.map("  - " + _)).mkString("\n"))

      Seq(
        if (jpsClasspath.isEmpty)
          Some("No <compileServer.plugin classpath=.../> extension found in the META-INF descriptors of the packaged jars.")
        else None,
        section(
          "Classes packaged in more than one jar (exclude the library in Dependencies.zinc or map it to the existing jar, " +
            "or extend PackagedPluginVerifier.AllowedDuplicateGroups if the duplication is intentional):",
          duplicates.map(d => s"${d.jars.mkString(", ")}: ${d.classes.size} classes, e.g. ${d.classes.take(3).mkString(", ")}")
        ),
        section("compileServer.plugin classpath entries (scalaCommunity.scala-impl.xml) without a jar under lib/:", missingClasspathEntries),
        section(
          "Jars in lib/jps that are neither on the compileServer.plugin classpath nor per Scala version artifacts " +
            "(add them to the classpath or stop packaging them):",
          unreferencedJpsJars
        ),
        section(
          "Marker classes not present exactly once on the compileServer.plugin classpath (see PackagedPluginVerifier.JpsClasspathMarkerClasses):",
          markerProblems.map(p => s"${p.className}: ${if (p.foundIn.isEmpty) "not found" else "found in " + p.foundIn.mkString(", ")}")
        ),
      ).flatten
    }
  }

  def verify(outputDir: File): Result = {
    val libDir = new File(outputDir, "lib")
    val jars = listJars(libDir).sortBy(_.getPath)
    def relativeToLib(jar: File): String = libDir.toPath.relativize(jar.toPath).toString.replace(File.separatorChar, '/')

    // class entry -> jars (relative to lib/) containing it, in a stable order
    val classIndex = mutable.LinkedHashMap.empty[String, Vector[String]]
    val descriptors = Seq.newBuilder[String]
    var scannedClasses = 0
    jars.foreach { jar =>
      val zip = new ZipFile(jar)
      try {
        zip.entries().asScala.foreach { entry =>
          val name = entry.getName
          if (isClassEntry(name)) {
            scannedClasses += 1
            classIndex.update(name, classIndex.getOrElse(name, Vector.empty) :+ relativeToLib(jar))
          } else if (isPluginDescriptor(name)) {
            descriptors += readEntry(zip, entry)
          }
        }
      } finally zip.close()
    }

    val jpsClasspath = descriptors.result()
      .flatMap(descriptor => CompileServerPluginClasspath.findAllMatchIn(descriptor).map(_.group(1)))
      .flatMap(_.split(';'))
      .map(_.trim)
      .filter(_.nonEmpty)
    val jpsClasspathSet = jpsClasspath.toSet

    val duplicates = classIndex.toSeq
      .collect { case (className, inJars) if inJars.size > 1 && !isAllowedDuplicate(inJars) => (inJars.sorted, className) }
      .groupBy(_._1)
      .map { case (inJars, pairs) => DuplicatedClasses(inJars, pairs.map(_._2).sorted) }
      .toSeq
      .sortBy(_.jars.mkString(","))

    val missingClasspathEntries = jpsClasspath.filterNot(entry => new File(libDir, entry).isFile)

    val unreferencedJpsJars = jars.map(relativeToLib)
      .filter(_.startsWith("jps/"))
      .filterNot(jar => jpsClasspathSet.contains(jar) || PerScalaVersionJpsJar.pattern.matcher(jar).matches())

    val markerProblems = JpsClasspathMarkerClasses.flatMap { className =>
      val foundIn = classIndex.getOrElse(className, Vector.empty).filter(jpsClasspathSet)
      if (foundIn.size == 1) None else Some(MarkerProblem(className, foundIn))
    }

    Result(jpsClasspath, duplicates, missingClasspathEntries, unreferencedJpsJars, markerProblems, jars.size, scannedClasses)
  }

  private def isAllowedDuplicate(jars: Seq[String]): Boolean =
    AllowedDuplicateGroups.exists(group => jars.forall(group))

  private def listJars(dir: File): Seq[File] =
    Option(dir.listFiles()).toSeq.flatten.flatMap { file =>
      if (file.isDirectory) listJars(file)
      else if (file.getName.endsWith(".jar")) Seq(file)
      else Seq.empty
    }

  // Multi-release versioned entries (META-INF/versions/<N>/...) and module descriptors are not application classes.
  private def isClassEntry(name: String): Boolean =
    name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class")

  // Plugin descriptors live in META-INF/, content module descriptors (lib/modules/*.jar) at the jar root.
  private def isPluginDescriptor(name: String): Boolean =
    name.endsWith(".xml") && (name.indexOf('/') < 0 || name.matches("META-INF/[^/]+\\.xml"))

  private def readEntry(zip: ZipFile, entry: ZipEntry): String = {
    val in = zip.getInputStream(entry)
    try new String(in.readAllBytes(), StandardCharsets.UTF_8)
    finally in.close()
  }
}
