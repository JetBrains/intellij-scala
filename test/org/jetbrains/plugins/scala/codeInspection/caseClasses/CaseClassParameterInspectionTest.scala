package org.jetbrains.plugins.scala.codeInspection.caseClasses

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.CaseClassParamInspection

/**
  * @author Nikolay.Tropin
  */
class CaseClassParameterInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[CaseClassParamInspection]

  override protected val description: String = ScalaBundle.message("val.on.case.class.param.redundant")

  def testSimpleParam(): Unit = checkTextHasError(s"case class A(${START}val x: Int$END)")

  def testSecondClause(): Unit = checkTextHasNoErrors(s"case class A(x: Int)(val s: String)")

  def testDefault(): Unit = {
    val text = s"case class A(${START}val x: Int = 1$END)"

    checkTextHasError(text)
    testQuickFix(text, "case class A(x: Int = 1)", ScalaBundle.message("remove.val"))
  }

  def testWithModifier(): Unit = checkTextHasNoErrors("case class A(protected val x: Int)")
}
