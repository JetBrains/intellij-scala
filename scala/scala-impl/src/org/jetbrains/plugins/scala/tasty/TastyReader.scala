package org.jetbrains.plugins.scala.tasty

import java.io.{File, FileNotFoundException}
import java.net.URLClassLoader
import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}
import java.util.jar.JarFile

import com.intellij.util.PathUtil

import scala.util.Using

object TastyReader {
  private val MajorVersion = 28
  private val MinorVersion = 0

  // A TASTy document is composed of a header, which contains a magic number 0x5CA1AB1F, a version number and a UUID.
  // * https://github.com/scala/scala/pull/9109/files#diff-f7bbafb9ed1dff384defaa69687349daa35b276a4320aa61046844b0014e0cb5R26
  // * https://github.com/scala/scala/blob/1360aef77125e993e8495dd59ce2983889688b54/src/compiler/scala/tools/tasty/TastyFormat.scala#L17
  // * https://github.com/scala/scala/pull/9109/files#diff-b57f5a78fc6ead7f134260b01093bed096c9dd51b12cc59e094c1a58a14eea1bR27
  private val Header: Array[Byte] = Array(0x5C, 0xA1, 0xAB, 0x1F, 0x80 | MajorVersion, 0x80 | MinorVersion).map(_.toByte)

  def read(classpath: String, bytes: Array[Byte], rightHandSide: Boolean): Option[TastyFile] = {
    if (!bytes.startsWith(Header)) {
      return None
    }

    val file = File.createTempFile("foo", ".tasty")
    try {
      Files.write(file.toPath, bytes, StandardOpenOption.TRUNCATE_EXISTING)
      read(api, classpath: String, file.getPath, rightHandSide)
    } finally {
      file.delete()
    }
  }

  // TODO fully-qualified name VS .tasty file path
  // There is no way to read a specific (one) .tasty file in a JAR using the API of https://github.com/lampepfl/dotty/blob/M2/tasty-inspector/src/scala/tasty/inspector/TastyInspector.scala?
  def read(tastyPath: TastyPath, rightHandSide: Boolean = true): Option[TastyFile] = {
    val tastyFilePath = tastyPath.className.replace('.', File.separatorChar) + ".tasty"
    if (tastyPath.classpath.endsWith(".jar")) {
      extracting(tastyPath.classpath, tastyFilePath)(read(api, "", _, rightHandSide))
    } else {
      val absoluteTastyFilePath = s"${tastyPath.classpath}${File.separator}$tastyFilePath"
      read(api, tastyPath.classpath, absoluteTastyFilePath, rightHandSide)
    }
  }

  private def extracting[T](jar: String, entry: String)(process: String => Option[T]): Option[T] = {
    Using(new JarFile(jar)) { jar =>
      Option(jar.getEntry(entry)).foreach { entry =>
        val file = File.createTempFile("foo", ".tasty")
        try {
          Using(jar.getInputStream(entry))(Files.copy(_, file.toPath, StandardCopyOption.REPLACE_EXISTING))
          return process(file.getAbsolutePath)
        } finally {
          file.delete()
        }
      }
    }
    None
  }

  // TODO Async, Progress, GC, error handling
  private lazy val api: TastyApi = {
    val jarFiles = {
      val tastyDirectory = tastyDirectoryFor(getClass)
      val tastyFiles = tastyDirectory.listFiles()
      Seq(
        "scala3-compiler",
        "scala3-interfaces",
        "scala3-library", // TODO Use scala3-library in lib/ (when there will be one)
        "scala3-tasty-inspector",
        "tasty-core",
        "tasty-runtime",
      ).map(prefix => tastyFiles.find(_.getName.startsWith(prefix)).getOrElse(throw new FileNotFoundException(prefix))) :+
        // TODO Why do we also need this library in the URL classloader? (it work fine using the main method, but not from IDEA)
        new File(tastyDirectory.getParent, s"scala-library.jar")
    }

    val consumeTastyImplClass = {
      val urls = jarFiles.map(file => file.toURI.toURL).toArray
      val loader = new URLClassLoader(urls, getClass.getClassLoader)
      loader.loadClass("org.jetbrains.plugins.scala.tasty.TastyImpl")
    }

    consumeTastyImplClass.getDeclaredConstructor().newInstance().asInstanceOf[TastyApi]
  }

  private def read(api: TastyApi,
                   classpath: String,
                   absoluteTastyFilePath: String,
                   rightHandSide: Boolean): Option[TastyFile] =
    api.read(classpath, absoluteTastyFilePath, rightHandSide)

  private def tastyDirectoryFor(aClass: Class[_]): File = {
    val libDirectory = {
      val jarPath = PathUtil.getJarPathForClass(aClass)
      if (jarPath.endsWith(".jar")) new File(jarPath).getParentFile
      else new File("target/plugin/Scala/lib")
    }
    val directory = new File(libDirectory, "tasty")
    assert(directory.exists, directory.toString)
    directory
  }

  // TODO Remove (convenience for debugging purposes)
  // NB: The plugin artifact must be build before running.
  // cd ~/IdeaProjects
  // git clone https://github.com/scala/scala3-example-project
  // cd scala3-example-project ; sbt compile
  def main(args: Array[String]): Unit = {
    val DottyExampleProject = System.getProperty("user.home") + "/IdeaProjects/scala3-example-project"

    def textAt(position: Position): String =
      if (position.start == -1) "<undefined>"
      else new String(Files.readAllBytes(Paths.get(DottyExampleProject + "/" + position.file))).substring(position.start, position.end)

    val exampleClasses = Seq(
      "ContextFunctions",
      "Conversion",
      "EnumTypes",
      "GivenInstances",
      "IntersectionTypes",
      "Main",
      "MultiversalEquality",
      "ParameterUntupling",
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-3.0.0-RC2/classes"
    assertExists(outputDir)

    exampleClasses.foreach { fqn =>
      println(fqn)

      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".tasty")
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".class")

      val file = read(TastyPath(outputDir, fqn)).get
      println(file.text)

      (file.references ++ file.types).sortBy {
        case it: ReferenceData @unchecked => it.position.start
        case it: TypeData @unchecked => it.position.start
      }.foreach {
        case it: ReferenceData @unchecked if it.getClass.getName.endsWith("ReferenceData") =>
          println("REF: " + textAt(it.position) + ", " + it)
        case it: TypeData @unchecked =>
          println("TPE: " + textAt(it.position) + ": " + it.presentation + ", " + it)
      }
    }
  }

  private def assertExists(path: String): Unit = assert(new File(path).exists, path)
}