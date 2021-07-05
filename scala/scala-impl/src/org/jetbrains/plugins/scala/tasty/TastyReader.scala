package org.jetbrains.plugins.scala.tasty

import com.intellij.util.PathUtil

import java.io.{File, FileNotFoundException}
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}

object TastyReader {
  def read(bytes: Array[Byte]): Option[(String, String)] = api.read(bytes)

  // TODO Access TastyImpl directly
  private lazy val api: TastyApi = {
    val jarFiles = {
      val tastyDirectory = tastyDirectoryFor(getClass)
      val tastyFiles = tastyDirectory.listFiles()
      Seq(
        "scala3-library", // TODO Use scala3-library in lib/ when there will be one
        "tasty-core",
        "tasty-runtime",
      ).map(prefix => tastyFiles.find(_.getName.startsWith(prefix)).getOrElse(throw new FileNotFoundException(prefix)))
    }

    val tastyImpl = {
      val urls = jarFiles.map(file => file.toURI.toURL).toArray
      val loader = new URLClassLoader(urls, getClass.getClassLoader)
      loader.loadClass("org.jetbrains.plugins.scala.tasty.TastyImpl")
    }

    tastyImpl.getDeclaredConstructor().newInstance().asInstanceOf[TastyApi]
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
  // git clone https://github.com/scala/scala3-example-project
  // cd scala3-example-project ; sbt compile
  def main(args: Array[String]): Unit = {
    val DottyExampleProject = System.getProperty("user.home") + "/IdeaProjects/scala3-example-project"

    val exampleClasses = Seq(
      "ContextFunctions",
      "EnumTypes",
      "GivenInstances",
      "IntersectionTypes",
      "Main",
      "MultiversalEquality",
      "ParameterUntupling",
      "PatternMatching",
      "StructuralTypes",
      "TraitParams",
      "TypeLambdas",
      "UnionTypes",
    )

    assertExists(DottyExampleProject)

    val outputDir = DottyExampleProject + "/target/scala-3.0.0/classes"
    assertExists(outputDir)

    exampleClasses.foreach { fqn =>
      val tastyFile = outputDir + "/" + fqn.replace('.', '/') + ".tasty"
      assertExists(tastyFile)

      val (name, text) = read(Files.readAllBytes(Paths.get(tastyFile))).get
      println(name + ":")
      println(text)
      println()
    }
  }

  private def assertExists(path: String): Unit = assert(new File(path).exists, path)
}