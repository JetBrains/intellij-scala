package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.net.URLClassLoader

import org.jetbrains.plugins.scala.DependencyManagerBase
import org.jetbrains.plugins.scala.DependencyManagerBase.DependencyDescription

object TastyReader {
  // TODO Remove when the project use Scala 2.13
  import scala.language.reflectiveCalls

  def read(classpath: String, className: String): Option[TastyFile] =
    Option(reader.read(classpath, className))

  // TODO Async, Progress, GC
  private val reader: TastyReader = {
    object Resolver extends DependencyManagerBase {
      override protected val artifactBlackList = Set.empty[String]
    }

    def jarFilesOfResolved(artifact: DependencyDescription): Seq[File] =
      Resolver.resolve(artifact.copy(isTransitive = true)).map(_.file)

    val jars = jarFilesOfResolved(DependencyDescription("ch.epfl.lamp", "dotty-tasty-inspector_0.22", "0.22.0-RC1"))

    val files = jars ++ Seq(
      new File("target/plugin/Scala/lib/tasty/tasty-compile.jar"),
      new File("target/plugin/Scala/lib/tasty/tasty-runtime.jar"),
      new File("target/plugin/Scala/lib/tasty/tasty-reader.jar")
    )

    val urls = files.map(file => file.toURI.toURL)
    urls.foreach(url => assert(new File(url.toURI).exists(), url.toString))
    val loader = new URLClassLoader(urls.toArray, IsolatingClassLoader.scalaStdLibIsolatingLoader(getClass.getClassLoader))
    val aClass = loader.loadClass("org.jetbrains.plugins.scala.tasty.TastyReaderImpl")

    aClass.newInstance().asInstanceOf[TastyReader]
  }

  // TODO Remove
  def main(args: Array[String]): Unit = {
    val result = read("/home/pavel/IdeaProjects/dotty-example-project/target/scala-0.22/classes", "ContextQueries").get
    println(result.text)
    println(result.references.foreach(println))
    println(result.types.foreach(println))
  }
}