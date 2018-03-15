package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Nikolay.Tropin
  * 29-Jan-18
  */
@Category(Array(classOf[PerfCycleTests]))
class ShapelessConformanceTest extends TypeConformanceTestBase {

  override implicit val version: ScalaVersion = Scala_2_11

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    IvyManagedLoader("com.chuusai" %% "shapeless" % "2.3.2") :: Nil

  def testWitnessSelectDynamicWrongLiteral(): Unit = doTest(
    s"""
       |object Test {
       |  type `"foo"` = shapeless.Witness.`"foo"`.T
       |}
       |val foo: Test.`"foo"` = "bar"
       |//False
     """.stripMargin
  )

  def testWitnessValSelectDynamicWrongLiteral(): Unit = doTest(
    s"""
       |object Test {
       |  val W = shapeless.Witness
       |  type `"foo"` = W.`"foo"`.T
       |}
       |val foo: Test.`"foo"` = "bar"
       |//False
     """.stripMargin
  )

}
