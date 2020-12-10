package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Paths, StandardCopyOption, StandardOpenOption}
import java.util.jar.JarFile

import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.buildinfo.BuildInfo

import scala.quoted.Reflection
import scala.tasty.inspector.{ConsumeTasty, TastyConsumer}
import scala.util.Using

object TastyReader {
  private val MajorVersion = 25
  private val MinorVersion = 1

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
    if (tastyPath.classpath.endsWith(".jar")) {
      extracting(tastyPath.classpath, tastyPath.className.replace('.', '/') + ".tasty")(read(api, "", _, rightHandSide))
    } else {
      read(api, tastyPath.classpath, tastyPath.classpath + "/" + tastyPath.className + ".tasty", rightHandSide)
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
  private lazy val api: ConsumeTasty = {
    val jarFiles = {
      val tastyDirectory = tastyDirectoryFor(getClass)
      val bundledJars =
        Seq(
          "tasty-runtime.jar",
          "tasty-inspector.jar",
          "tasty-core.jar",
          "scala-interfaces.jar",
          "scala-compiler.jar")
          .map(new File(tastyDirectory, _)) ++
          Seq( // TODO Why do we also need those libraries in the URL classloader? (it work fine using the main method, but not from IDEA)
            s"scala-library-${BuildInfo.scalaVersion}.jar",
            "scala3-library_3.0.0-M2-3.0.0-M2.jar")
            .map(new File(tastyDirectory.getParent, _))
      bundledJars.foreach(file => assert(file.exists(), "Not found: " + file.getPath))
      bundledJars
    }

    val consumeTastyImplClass = {
      val urls = jarFiles.map(file => file.toURI.toURL).toArray
      val loader = new URLClassLoader(urls, getClass.getClassLoader)
      loader.loadClass("scala.tasty.compat.ConsumeTastyImpl")
    }

    consumeTastyImplClass.getDeclaredConstructor().newInstance().asInstanceOf[ConsumeTasty]
  }

  private def read(consumeTasty: ConsumeTasty, classpath: String, className: String, rightHandSide: Boolean): Option[TastyFile] = {
    // TODO An ability to detect errors, https://github.com/lampepfl/dotty-feature-requests/issues/101
    var result = Option.empty[TastyFile]

    val tastyConsumer = new TastyConsumer {
      override def apply(reflect: Reflection)(tree: reflect.delegate.Tree): Unit = {
        val printer = new SourceCodePrinter[reflect.type](reflect, rightHandSide)
        val text = printer.showTree(tree)
        def file(path: String) = {
          val i = path.replace('\\', '/').lastIndexOf("/")
          if (i > 0) path.substring(i + 1) else path
        }
        val source = printer.sources.headOption.map(file).getOrElse("unknown.scala")
        result = Some(TastyFile(source, text, printer.references, printer.types))
      }
    }

    consumeTasty.apply(classpath, List(className), tastyConsumer)

    result
  }

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
  // git clone https://github.com/lampepfl/dotty-example-project.git
  // cd dotty-example-project ; sbt compile
  def main(args: Array[String]): Unit = {
    val DottyExampleProject = System.getProperty("user.home") + "/IdeaProjects/dotty-example-project"

    def textAt(position: Position): String =
      if (position.start == -1) "<undefined>"
      else new String(Files.readAllBytes(Paths.get(DottyExampleProject + "/" + position.file))).substring(position.start, position.end)

    val exampleClasses = Seq(
      "AutoParamTupling",
      "ContextQueries",
      "Conversion",
      "EnumTypes",
      "ImpliedInstances",
      "IntersectionTypes",
      "Main",
      "MultiversalEquality",
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-3.0.0-M2/classes"
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