package org.jetbrains.plugins.scala.lang.typeConformance.generated

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel


class TypeConformanceZioTest extends TypeConformanceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.languageLevel == ScalaLanguageLevel.Scala_2_13

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("dev.zio" %% "zio" % "1.0.0-RC18-2")

  def testSCL17210(): Unit = {
    doTest(
      s"""import zio.{Has, ZLayer}
         |import zio.console.Console
         |import zio.random.Random
         |
         |type Example = Has[Example.Service]
         |object Example {
         |  trait Service
         |}
         |
         |val live: ZLayer[Console with Random, Nothing, Example] =
         |  ZLayer.fromServices[Console.Service, Random.Service, Example.Service] { (console, random) =>
         |    new Example.Service {}
         |  }
         |//true
      """.stripMargin)
  }

  def testSCL17210_differentOrder(): Unit = {
    doTest(
      s"""import zio.{Has, ZLayer}
         |import zio.console.Console
         |import zio.random.Random
         |
         |type Example = Has[Example.Service]
         |object Example {
         |  trait Service
         |}
         |
         |val live: ZLayer[Random with Console, Nothing, Example] =
         |  ZLayer.fromServices[Console.Service, Random.Service, Example.Service] { (console, random) =>
         |    new Example.Service {}
         |  }
         |//true
      """.stripMargin)
  }


}
