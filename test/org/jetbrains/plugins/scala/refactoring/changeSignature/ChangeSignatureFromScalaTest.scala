package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.PsiMember
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessorBase, ParameterInfo}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureHandler, ScalaParameterInfo}
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 2014-09-05
 */
class ChangeSignatureFromScalaTest extends ChangeSignatureTestBase {
  override def folderPath: String = baseRootPath() + "changeSignature/fromScala/"

  override def mainFileName(testName: String) = testName + ".scala"
  override def secondFileName(testName: String) = testName + ".java"
  override def mainFileAfterName(testName: String) = testName + "_after.scala"
  override def secondFileAfterName(testName: String) = testName + "_after.java"

  override def findTargetElement: PsiMember = {
    val element = new ScalaChangeSignatureHandler().findTargetMember(getFileAdapter, getEditorAdapter)
    assertTrue("<caret> is not on method name", element.isInstanceOf[ScMethodLike])
    element.asInstanceOf[ScMethodLike]
  }

  override def processor(newVisibility: String,
                         newName: String,
                         newReturnType: String,
                         newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {
    scalaProcessor(newVisibility, newName, newReturnType, newParams, isAddDefaultValue)
  }

  private def parameterInfo(name: String, oldIdx: Int, tpe: ScType, defVal: String = "", isRep: Boolean = false, isByName: Boolean = false) = {
    new ScalaParameterInfo(name, oldIdx, tpe, getProjectAdapter, isRep, isByName, defVal)
  }

  def testSimpleMethod() = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("ii", 0, Int), parameterInfo("b", 2, Boolean))
    doTest(null, "bar", null, Seq(params))
  }

  def testSimpleMethodAdd() = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("s", -1, AnyRef, "\"hi\""), parameterInfo("b", 1, Boolean))
    doTest(null, "foo", null, Seq(params))
  }

  def testAddWithDefault() = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("s", -1, AnyRef, "\"hi\""), parameterInfo("b", 1, Boolean))
    doTest(null, "foo", null, Seq(params))
  }

  def testParameterless() = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", -1, Int, "1"))
    doTest(null, "bar", null, Seq(params))
  }

  def testAddByName() = {
    val params = Seq(parameterInfo("x", 0, Int), parameterInfo("s", 1, AnyRef, isByName = true))
    doTest(null, "foo", null, Seq(params))
  }

  def testReturnTypeChange() = {
    val params = Seq(Seq.empty)
    doTest(null, "foo", "Unit", params)
  }

  def testGenerics() = {
    def tpe = createTypeFromText("T", targetMethod, targetMethod).get
    doTest(null, "foo", "T", Seq(Seq(parameterInfo("t", 0, tpe))))
  }

  def testSecConstructor() = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("j", -1, Int, "0"))
    doTest(null, "Constructor", null, Seq(params))
  }

  def testPrimConstructor() = {
    isAddDefaultValue = false
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", -1, Boolean, "true"))
    doTest("protected", "Constructor", null, Seq(params))
  }

  def testDifferentParamNames() = {
    val params = Seq(parameterInfo("newName", 0, Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testPrimConstructorDefault() = {
    isAddDefaultValue = true
    val params = Seq(parameterInfo("i", 0, Int), parameterInfo("b", -1, Boolean, "true"))
    doTest("protected", "Constructor", null, Seq(params))
  }

  def testAddNewClauseWithDefault() = {
    isAddDefaultValue = true
    val params = Seq(Seq(parameterInfo("b", -1, Boolean, "true")), Seq(parameterInfo("x", 0, Int), parameterInfo("y", -1, Int, "0")))
    doTest(null, "foo", null, params)
  }

  def testAddNewClause() = {
    isAddDefaultValue = false
    val params = Seq(Seq(parameterInfo("b", -1, Boolean, "true")), Seq(parameterInfo("x", 0, Int), parameterInfo("y", -1, Int, "0")))
    doTest(null, "foo", null, params)
  }

  def testRemoveClause() = {
    val params = Seq(parameterInfo("b", 1, Boolean), parameterInfo("i", 0, Int))
    doTest(null, "RemoveClauseConstructor", null, Seq(params))
  }

  def testCaseClass(): Unit = {
    val params = Seq(parameterInfo("number", 1, Int), parameterInfo("char", 0, Char), parameterInfo("b", -1, Boolean, "true"))
    doTest(null, "MyClass", null, Seq(params))
  }
}
