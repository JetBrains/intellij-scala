package org.jetbrains.plugins.scala
package lang.typeInference.generated

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class TypeInferenceScalazTest extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version  <= LatestScalaVersions.Scala_2_11

  override def folderPath: String = super.folderPath + "scalaz/"

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader("org.scalaz" %% "scalaz-core" % "7.1.0")

  def testSCL3819(): Unit = doTest()

  def testSCL4033(): Unit = doTest()

  def testSCL4352(): Unit = doTest()

  def testSCL4468(): Unit = doTest()

  def testSCL4912(): Unit = doTest()

  def testSCL6417(): Unit = doTest()

  def testSCL6417_Performance(): Unit = doTest()

  def testSCL7669(): Unit = {
    doTest(
      s"""
         |import scalaz._
         |import scalaz.std.list._
         |import scalaz.syntax.monad._
         |
         |type Foo[A] = ReaderWriterState[String, List[Int], Unit, A]
         |
         |def foo[T](f: ⇒ T): Foo[T] = ReaderWriterState { (a, b) ⇒ (Nil, f, ()) }
         |
         |${START}foo(1) >> foo(2) >> foo(2)$END
         |//Foo[Int]
      """.stripMargin)
  }

  def testSCL9219(): Unit = {
    doTest(
      s"""
         |import scalaz._, Scalaz._
         |val e: Either[String, Int] = Right(12)
         |val o: Option[Either[String, Int]] = Some(e)
         |//val r: Either[String, Option[Int]] = o.sequenceU
         |val r = ${START}o.sequenceU$END
         |
        |//Either[String, Option[Int]]
      """.stripMargin)
  }

}
