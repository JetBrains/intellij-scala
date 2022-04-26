package org.jetbrains.plugins.scala.tasty

import java.io.File
import java.nio.file.{Files, Paths}

object TastyReader {
  def read(bytes: Array[Byte]): Option[(String, String)] = api.read(bytes)

  private lazy val api = new TastyImpl()

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

    val outputDir = DottyExampleProject + "/target/scala-3.1.2/classes"
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