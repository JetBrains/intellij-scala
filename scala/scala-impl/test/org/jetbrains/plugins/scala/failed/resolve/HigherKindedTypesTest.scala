package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 02.09.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class HigherKindedTypesTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]

  override protected val description: String = "Cannot resolve symbol hello"

  def testSCL10432(): Unit = {
    checkTextHasError(
      s"""
         |sealed abstract class CtorType[-P]
         |case class Hello[-P >: Int <: AnyVal]() extends CtorType[P] {
         |  def hello(p: P) = 123
         |}
         |
         |trait Component[-P, CT[-p] <: CtorType[p]] {
         |  val ctor: CT[P]
         |}
         |
         |implicit def toCtorOps[P >: Int <: AnyVal, CT[-p] <: CtorType[p]](base: Component[P, CT]) =
         |  base.ctor
         |
         |val example: Component[Int, Hello] = ???
         |example.ctor.hello(123)
         |val left: Int = example.hello(123)
      """.stripMargin)
  }

  def testSCL12929(): Unit = {
    checkTextHasError(
      s"""
         |trait A {
         |  val x: Int
         |}
         |
         |trait B {
         |  val y: Int
         |}
         |
         |class C[T[_] <: A with B](a: T[Int]){
         |  val b = a.x
         |}
      """.stripMargin)
  }
}
