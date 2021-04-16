package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.internal.AnnotatorBasedErrorInspection

/**
  * @author adkozlov
  */
class OverridingAnnotatorTest3 extends ScalaInspectionTestBase {

  override protected def shouldPass: Boolean = false

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[AnnotatorBasedErrorInspection]

  override protected val description: String =
    "overriding variable name in class Abs of type String"

  def testScl7536(): Unit = {
    checkTextHasError(
      s"""
         |class Abs(var name: String){ }         |
         |class AbsImpl(${START}override$END var name: String) extends Abs(name){ }
      """.stripMargin)
  }
}
