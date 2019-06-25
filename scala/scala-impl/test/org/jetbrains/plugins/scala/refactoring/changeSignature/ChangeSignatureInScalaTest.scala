package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.PsiMember
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ParameterInfo}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureHandler, ScalaParameterInfo}
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 2014-09-11
 */
class ChangeSignatureInScalaTest extends ChangeSignatureTestBase {

  override def findTargetElement: PsiMember = {
    val element = new ScalaChangeSignatureHandler().findTargetMember(getFileAdapter, getEditorAdapter)
    assertTrue("<caret> is not on method name", element.isInstanceOf[ScMethodLike])
    element.asInstanceOf[ScMethodLike]
  }

  override def folderPath: String = baseRootPath + "changeSignature/inScala/"

  override def processor(newVisibility: String,
                         newName: String,
                         newReturnType: String,
                         newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {
    scalaProcessor(newVisibility, newName, newReturnType, newParams, isAddDefaultValue)
  }

  override def mainFileName(testName: String): String = testName + ".scala"
  override def mainFileAfterName(testName: String): String = testName + "_after.scala"
  override def secondFileName(testName: String): String = null
  override def secondFileAfterName(testName: String): String = null

  private def parameterInfo(name: String, oldIdx: Int, tpe: ScType, defVal: String = "", isRep: Boolean = false, isByName: Boolean = false) = {
    new ScalaParameterInfo(name, oldIdx, tpe, getProjectAdapter, isRep, isByName, defVal)
  }

  def testVisibility(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", -1, Int, "1"))
    doTest("protected", "foo", null, Seq(params))
  }

  def testAddRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean),
      parameterInfo("xs", -1, Int, isRep = true, defVal = "1"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddRepeatedWithoutDefault(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("xs", -1, Int, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testMakeRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean, isRep = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testRemoveRepeatedParam(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", 1, Boolean))
    doTest(null, "foo", null, Seq(params))
  }

  def testNoDefaultArg(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testNoDefaultArg2(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunWithDefaultArg(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunModifyCall(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonFunManyParams(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("j", 1, Int),
      parameterInfo("b", 2, Boolean),
      parameterInfo("s", -1, AnyRef, "\"\""))
    doTest(null,"foo", null, Seq(params))
  }

  def testLocalFunction(): Unit = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("s", -1, Boolean, "true"))
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
    
    doTest(null, "local", null, Seq(params), settings = TypeAnnotationSettings.noTypeAnnotationForLocal(settings))
  }

  def testImported(): Unit = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", -1, Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddClauseConstructorVararg(): Unit = {
    isAddDefaultValue = false
    val params = Seq(Seq(parameterInfo("b", 0, Boolean)), Seq(parameterInfo("x", -1, Int, "10"), parameterInfo("i", 1, Int, isRep = true)))
    doTest(null, "AddClauseConstructorVararg", null, params)
  }

  def testCaseClass(): Unit = {
    isAddDefaultValue = true
    val params = Seq(
      Seq(parameterInfo("ii", 0, Int), parameterInfo("argss", 2, Int, isRep = true)),
      Seq(parameterInfo("cc", 1, Char), parameterInfo("b", -1, Boolean, "true"))
    )
    doTest(null, "CClass", null, params)
  }
}
