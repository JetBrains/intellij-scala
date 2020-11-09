package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}

import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription

import scala.quoted.show.SyntaxHighlight
import scala.tasty.compat.{ConsumeTasty, Reflection, TastyConsumer}

object TastyReader {
  def read(tastyPath: TastyPath, rightHandSize: Boolean = true): Option[TastyFile] =
    read(api, tastyPath.classpath, tastyPath.className, rightHandSize)

  // The "dotty-tasty-inspector" transitively depends on many unnecessary libraries.
  private val RequiredLibraries = Seq(
    "scala3-interfaces",
    "scala3-compiler",
    "scala3-tasty-inspector",
    "tasty-core",
    // TODO Why do we also need those libraries in the URL classloader? (tasty-example works fine without them)
    "scala-library",
    "scala3-library",
  )

  // TODO Async, Progress, GC, error handling
  private lazy val api: ConsumeTasty = {
    val jarFiles = {
      val resolvedJars = {
        val Resolver = new DependencyManagerBase {override protected val artifactBlackList = Set.empty[String] }
        // TODO TASTy inspect: an ability to detect .tasty file version, https://github.com/lampepfl/dotty-feature-requests/issues/99
        // TODO TASTy inspect: make dotty-compiler depend on tasty-inspector https://github.com/lampepfl/dotty-feature-requests/issues/100
        // TODO Introduce the version variable
        val tastyInspectorDependency = DependencyDescription("org.scala-lang", "scala3-tasty-inspector_3.0.0-M1", "3.0.0-M1", isTransitive = true)
        Resolver.resolve(tastyInspectorDependency).map(_.file).filter(jar => RequiredLibraries.exists(jar.getPath.contains))
      }

      val bundledJar = new File(tastyDirectoryFor(getClass), "tasty-runtime.jar")

      resolvedJars :+ bundledJar
    }

    val consumeTastyImplClass = {
      val urls = jarFiles.map(file => file.toURI.toURL).toArray
      val loader = new URLClassLoader(urls, getClass.getClassLoader)
      loader.loadClass("scala.tasty.compat.ConsumeTastyImpl")
    }

    consumeTastyImplClass.getDeclaredConstructor().newInstance().asInstanceOf[ConsumeTasty]
  }

  private def read(consumeTasty: ConsumeTasty, classpath: String, className: String, rightHandSize: Boolean): Option[TastyFile] = {
    // TODO An ability to detect errors, https://github.com/lampepfl/dotty-feature-requests/issues/101
    var result = Option.empty[TastyFile]

    val tastyConsumer = new TastyConsumer {
      override def apply(reflect: Reflection)(tree: reflect.delegate.Tree): Unit = {
        val printer = new SourceCodePrinter[reflect.type](reflect, rightHandSize)(SyntaxHighlight.plain)
        val text = printer.showTree(tree)(reflect.delegate.rootContext)
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
      "NamedTypeArguments",
//      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-0.27/classes"
    assertExists(outputDir)

    exampleClasses.foreach { fqn =>
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".class")
      assertExists(outputDir + "/" + fqn.replace('.', '/') + ".tasty")

      println(fqn)
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