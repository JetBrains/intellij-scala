package org.jetbrains.plugins.scala.lang.macros

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase

/**
  * Nikolay.Tropin
  * 29-Jan-18
  */
class ShapelessConformanceTest extends TypeConformanceTestBase {

  override implicit val version: ScalaVersion = Scala_2_13

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    IvyManagedLoader("com.chuusai" %% "shapeless" % "2.3.3") :: Nil

  def testWitnessSelectDynamic(): Unit = doTest(
    s"""
       |object Test {
       |  type `"foo"` = shapeless.Witness.`"foo"`.T
       |}
       |val foo: Test.`"foo"` = "foo"
       |//True
     """.stripMargin
  )

  def testWitnessValSelectDynamic(): Unit = doTest(
    s"""
       |object Test {
       |  val W = shapeless.Witness
       |  type `"foo"` = W.`"foo"`.T
       |}
       |val foo: Test.`"foo"` = "foo"
       |//True
     """.stripMargin
  )

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
