package org.jetbrains.plugins.scala.codeInspection.caseClasses

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.CaseClassParamInspection

/**
  * @author Nikolay.Tropin
  */
class CaseClassParameterInspectionTest extends ScalaLightInspectionFixtureTestAdapter  {
  override protected def annotation: String = ScalaBundle.message("val.on.case.class.param.redundant")

  override protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[CaseClassParamInspection]

  def testSimpleParam(): Unit = check(s"case class A(${START}val x: Int$END)")

  def testSecondClause(): Unit = checkTextHasNoErrors(s"case class A(x: Int)(val s: String)")

  def testDefault(): Unit = {
    check(s"case class A(${START}val x: Int = 1$END)")
    testFix(s"case class A(${START}val x: Int = 1$END)", "case class A(x: Int = 1)", ScalaBundle.message("remove.val"))
  }

  def testWithModifier(): Unit = checkTextHasNoErrors("case class A(protected val x: Int)")
}
