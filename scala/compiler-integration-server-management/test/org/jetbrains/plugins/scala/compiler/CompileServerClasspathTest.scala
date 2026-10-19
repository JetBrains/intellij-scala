package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.junit.jupiter.api.Assertions.{assertNotNull, assertTrue}
import org.junit.jupiter.api.Test

import java.io.File
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * The jars of the Scala Compile Server are listed by hand in two places: [[CompileServerLauncher.compileServerJars]]
 * (the compile server process) and the `compileServer.plugin` classpath in `scala-plugin-common.xml` (the JPS build
 * process). This test keeps the two lists consistent with each other and with the packaged plugin, which the tests
 * run against. The packaged plugin itself is verified by `PackagedPluginVerifier` in the sbt build.
 */
class CompileServerClasspathTest:

  /** Jars only the JPS build process needs. The file names carry versions, hence prefixes. */
  private val jpsOnlyJarPrefixes = Seq("spray-json", "scala-compiler-indices-protocol")

  /** Jars only the compile server process needs. */
  private val compileServerOnlyJars = Set("scala-reflect.jar", "scala-nailgun-runner.jar", "repl-interface.jar")

  /** One class per library the compile server process needs, each must be present exactly once. */
  private val markerClasses = Seq(
    "scala/Option.class",                                                                   // compile-server-scala-library.jar
    "scala/Tuple.class",                                                                    // compile-server-scala3-library_3.jar
    "scala/reflect/api/Types.class",                                                        // scala-reflect.jar
    "scala/xml/Node.class",                                                                 // scala-xml.jar
    "scala/util/parsing/combinator/RegexParsers.class",                                     // scala-parser-combinators.jar
    "scala/collection/parallel/ParIterable.class",                                          // incremental-compiler.jar, Zinc needs it
    "sbt/internal/inc/IncrementalCompilerImpl.class",                                       // incremental-compiler.jar
    "xsbti/Logger.class",                                                                   // sbt-interface.jar
    "xsbti/compile/CompileAnalysis.class",                                                  // compiler-interface.jar
    "com/facebook/nailgun/NGServer.class",                                                  // nailgun.jar
    "org/jetbrains/plugins/scala/nailgun/NailgunRunner.class",                              // scala-nailgun-runner.jar
    "org/jetbrains/plugins/scala/compiler/data/Arguments.class",                            // compiler-shared.jar
    "org/jetbrains/plugins/scala/compiler/references/Messages.class",                       // scala-jps.jar
    "org/jetbrains/plugins/scala/util/JarManifestUtils.class",                              // utils_rt.jar
    "org/jetbrains/jps/incremental/scala/remote/Main.class",                                // compile-server.jar
    "org/jetbrains/jps/incremental/scala/ScalaBuilder.class",                               // compiler-jps.jar
    "org/jetbrains/jps/incremental/scala/local/worksheet/repl_interface/ILoopWrapper.class", // repl-interface.jar
  )

  private val compileServerPluginClasspath = """<compileServer\.plugin\s+classpath="([^"]+)"""".r

  /** The jars of the compile server classpath which are shipped with the plugin (the rest are IntelliJ platform jars). */
  private def pluginJars: Seq[Path] =
    CompileServerLauncher.compileServerJars.filter(_.startsWith(ScalaPluginJars.libRoot))

  private def relativeToLib(jar: Path): String =
    ScalaPluginJars.libRoot.relativize(jar).toString.replace(File.separatorChar, '/')

  private def jpsClasspath: Seq[String] =
    val stream = ScalaPluginJars.getClass.getClassLoader.getResourceAsStream("META-INF/scala-plugin-common.xml")
    assertNotNull(stream, "META-INF/scala-plugin-common.xml not found on the classpath")
    val descriptor = Using.resource(stream)(Source.fromInputStream(_, "UTF-8").mkString)
    val entries = compileServerPluginClasspath.findAllMatchIn(descriptor)
      .flatMap(_.group(1).split(';'))
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSeq
    assertTrue(entries.nonEmpty, "No compileServer.plugin classpath found in META-INF/scala-plugin-common.xml")
    entries

  private def classEntries(jar: Path): Seq[String] =
    Using.resource(new ZipFile(jar.toFile)) { zip =>
      zip.entries.asScala
        .map(_.getName)
        .filter(name => name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class"))
        .toVector
    }

  /** Class entry -> jars (relative to `lib/`) of the compile server classpath containing it. */
  private def classIndex: Map[String, Seq[String]] =
    pluginJars.flatMap(jar => classEntries(jar).map(_ -> relativeToLib(jar))).groupMap(_._1)(_._2)

  @Test
  def compileServerJarsExist(): Unit =
    val missing = CompileServerLauncher.compileServerJars.filterNot(Files.isRegularFile(_))
    assertTrue(missing.isEmpty, s"Jars of the compile server classpath do not exist (is the plugin packaged?): ${missing.mkString(", ")}")

  @Test
  def jpsClasspathEntriesExist(): Unit =
    val missing = jpsClasspath.filterNot(entry => Files.isRegularFile(ScalaPluginJars.libRoot.resolve(entry)))
    assertTrue(missing.isEmpty, s"compileServer.plugin classpath entries do not exist under ${ScalaPluginJars.libRoot}: ${missing.mkString(", ")}")

  @Test
  def noClassIsPackagedTwiceOnTheCompileServerClasspath(): Unit =
    val duplicated = classIndex.filter(_._2.size > 1)
    val summary = duplicated.groupMap(_._2.sorted)(_._1).map { case (jars, classes) =>
      s"${jars.mkString(", ")}: ${classes.size} classes, e.g. ${classes.toSeq.sorted.take(3).mkString(", ")}"
    }
    assertTrue(duplicated.isEmpty, s"Classes present in more than one jar of the compile server classpath:\n${summary.mkString("\n")}")

  @Test
  def everyRequiredLibraryIsOnTheCompileServerClasspathOnce(): Unit =
    val index = classIndex
    val problems = markerClasses.flatMap { className =>
      index.getOrElse(className, Seq.empty) match
        case Seq(_) => None
        case Seq()  => Some(s"$className: not found")
        case found  => Some(s"$className: found in ${found.mkString(", ")}")
    }
    assertTrue(problems.isEmpty, s"Marker classes not present exactly once on the compile server classpath:\n${problems.mkString("\n")}")

  @Test
  def jpsAndCompileServerClasspathsAgree(): Unit =
    val jps = jpsClasspath.toSet
    val compileServer = pluginJars.map(relativeToLib).toSet
    val onlyInJps = (jps -- compileServer).filterNot(jar => jpsOnlyJarPrefixes.exists(jar.startsWith))
    val onlyInCompileServer = (compileServer -- jps) -- compileServerOnlyJars
    assertTrue(
      onlyInJps.isEmpty && onlyInCompileServer.isEmpty,
      s"""The compileServer.plugin classpath (scala-plugin-common.xml) and CompileServerLauncher.compileServerJars differ.
         |Add the jar to the other list, or record the difference in this test if it is intentional.
         |  only on the JPS classpath: ${onlyInJps.toSeq.sorted.mkString(", ")}
         |  only on the compile server classpath: ${onlyInCompileServer.toSeq.sorted.mkString(", ")}""".stripMargin
    )
