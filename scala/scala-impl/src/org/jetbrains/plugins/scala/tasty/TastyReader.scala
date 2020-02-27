package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.net.URLClassLoader

import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription

object TastyReader {
  // TODO Remove when the project use Scala 2.13
  import scala.language.reflectiveCalls

  def readText(classpath: String, className: String): Option[String] =
    read(classpath, className).map(_.text)

  def read(classpath: String, className: String): Option[TastyFile] =
    Option(reader.read(classpath, className))

  // TODO Async, Progress, GC, error handling
  private lazy val reader: TastyReader = {
    val jarFiles = {
      val resolvedJars = {
        val Resolver = new DependencyManagerBase {override protected val artifactBlackList = Set.empty[String] }
        // TODO TASTy inspect: an ability to detect .tasty file version, https://github.com/lampepfl/dotty-feature-requests/issues/99
        // TODO TASTy inspect: make dotty-compiler depend on tasty-inspector https://github.com/lampepfl/dotty-feature-requests/issues/100
        val tastyInspectorDependency = DependencyDescription("ch.epfl.lamp", "dotty-tasty-inspector_0.22", "0.22.0-RC1", isTransitive = true)
        Resolver.resolve(tastyInspectorDependency).map(_.file)
      }

      val bundledJars = {
        val tastyDirectory = new File(new File(PathUtil.getJarPathForClass(getClass)).getParentFile, "tasty")
        assert(tastyDirectory.exists, tastyDirectory.toString)
        Seq("tasty-compile.jar", "tasty-runtime.jar", "tasty-reader.jar").map(new File(tastyDirectory, _))
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

  // TODO Remove
  def main(args: Array[String]): Unit = {
    val result = read("/home/pavel/IdeaProjects/dotty-example-project/target/scala-0.22/classes", "ContextQueries").get
    println(result.text)
    println(result.references.foreach(println))
    println(result.types.foreach(println))
  }
}