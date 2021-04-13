package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection

/**
  * Created by Roman.Shein on 02.09.2016.
  */
class HigherKindedTypesTest extends ScalaInspectionTestBase {

  override protected def shouldPass: Boolean = false

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]

  override protected val description: String = "Cannot resolve symbol hello"

  def testSCL12929(): Unit = {
    checkTextHasNoErrors(
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
