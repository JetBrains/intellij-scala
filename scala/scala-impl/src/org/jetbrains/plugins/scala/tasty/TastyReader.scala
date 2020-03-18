package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}

import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription

import scala.collection.JavaConverters._

object TastyReader {
  // TODO Remove when the project use Scala 2.13
  import scala.language.reflectiveCalls

  def read(classpath: String, className: String): Option[TastyFile] =
    Option(reader.read(classpath, className))

  def read(containingFile: PsiFile): Option[TastyFile] =
    for {
      Location(outputDirectory, className) <- compiledLocationOf(containingFile)
      result <- TastyReader.read(outputDirectory, className)
    } yield result

  def readText(classpath: String, className: String): Option[String] =
    read(classpath, className).map(_.text)

  // TODO Async, Progress, GC, error handling
  private lazy val reader: TastyReader = {
    val jarFiles = {
      val resolvedJars = {
        val Resolver = new DependencyManagerBase {override protected val artifactBlackList = Set.empty[String] }
        // TODO TASTy inspect: an ability to detect .tasty file version, https://github.com/lampepfl/dotty-feature-requests/issues/99
        // TODO TASTy inspect: make dotty-compiler depend on tasty-inspector https://github.com/lampepfl/dotty-feature-requests/issues/100
        // TODO Introduce the version variable
        val tastyInspectorDependency = DependencyDescription("ch.epfl.lamp", "dotty-tasty-inspector_0.23", "0.23.0-RC1", isTransitive = true)
        Resolver.resolve(tastyInspectorDependency).map(_.file)
      }

      val bundledJars = {
        val base = tastyDirectoryFor(getClass)
        Seq("tasty-compile.jar", "tasty-runtime.jar", "tasty-reader.jar").map(new File(base, _))
      }

      resolvedJars ++ bundledJars
    }

    jarFiles.foreach(file => assert(file.exists(), file.toString))

    val tastyReaderImplClass = {
      val urls = jarFiles.map(file => file.toURI.toURL).toArray
      val loader = new URLClassLoader(urls, IsolatingClassLoader.scalaStdLibIsolatingLoader(getClass.getClassLoader))
      loader.loadClass("org.jetbrains.plugins.scala.tasty.TastyReaderImpl")
    }

    tastyReaderImplClass.newInstance().asInstanceOf[TastyReader]
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
  def main(args: Array[String]): Unit = {
    def textAt(position: Position): String = {
      val line = Files.readAllLines(Paths.get(position.file)).asScala(position.startLine)
      line.substring(position.startColumn, position.endColumn)
    }

    val home = System.getProperty("user.home")

    val exampleClasses = Seq(
      "AutoParamTupling",
      "ContextQueries",
      "Conversion",
      "Conversion",
      "ImpliedInstances",
      "IntersectionTypes",
      "MultiversalEquality",
      "NamedTypeArguments",
      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    exampleClasses.foreach { fqn =>
      println(fqn)
      val file = read(home + "/IdeaProjects/dotty-example-project/target/scala-0.23/classes", fqn).get
      println(file.text)

      (file.references ++ file.types).sortBy {
        case it: ReferenceData => (it.position.startLine, it.position.startColumn)
        case it: TypeData => (it.position.startLine, it.position.startColumn)
      }.foreach {
        case it: ReferenceData if it.getClass.getName.endsWith("ReferenceData") =>
          println("REF: " + textAt(it.position) + ", " + it)
        case it: TypeData =>
          println("TPE: " + textAt(it.position) + ": " + it.presentation + ", " + it)
      }
    }
  }
}