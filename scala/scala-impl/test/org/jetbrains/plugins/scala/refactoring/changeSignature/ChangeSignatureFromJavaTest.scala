package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.{PsiEllipsisType, PsiMember, PsiMethod}
import com.intellij.refactoring.changeSignature._
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants
import org.junit.Assert._

class ChangeSignatureFromJavaTest extends ChangeSignatureTestBase {

  override def folderPath: String = super.folderPath + "changeSignature/fromJava/"

  override def mainFileName(testName: String) = testName + ".java"

  override def secondFileName(testName: String) = testName + ".scala"

  override def mainFileAfterName(testName: String) = testName + "_after.java"

  override def secondFileAfterName(testName: String) = testName + "_after.scala"

  override def processor(newVisibility: String,
                         newName: String,
                         newReturnType: String,
                         newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {
    javaProcessor(newVisibility, newName, newReturnType, newParams)
  }

  override def findTargetElement: PsiMember = {
    val element = new JavaChangeSignatureHandler().findTargetMember(getFile, getEditor)
    assertTrue("<caret> is not on method name", element.isInstanceOf[PsiMethod])
    element.asInstanceOf[PsiMethod]
  }

  def testStaticMethod(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "ii", PsiTypeConstants.Int),
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"))
    doTest(null, "bar", null, Seq(params))
  }

  def testInstanceMethod(): Unit = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"),
      new ParameterInfoImpl(0, "ii", PsiTypeConstants.Int)
    )
    doTest(null, "bar", null, Seq(newParams))
  }

  def testOverriders(): Unit = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"),
      new ParameterInfoImpl(0, "ii", PsiTypeConstants.Int)
    )
    doTest(null, "bar", "boolean", Seq(newParams))
  }

  def testOverriderInAnonClass(): Unit = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"),
      new ParameterInfoImpl(0, "ii", PsiTypeConstants.Int)
    )
    doTest(null, "bar", "boolean", Seq(newParams))
  }

  def testParameterlessOverriders(): Unit = {
    doTest(null, "bar", null, Seq(Seq.empty))
  }

  def testParameterlessOverriders2(): Unit = {
    val params = Seq(new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"))
    doTest(null, "bar", null, Seq(params))
  }

  def testInfixUsage(): Unit = {
    val params = Seq(new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"))
    doTest(null, "print", null, Seq(params))
  }

  def testInfixUsage2(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiTypeConstants.Int),
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"))
    doTest(null, "print", null, Seq(params))
  }

  def testInfixUsageWithTuple(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiTypeConstants.Int),
      new ParameterInfoImpl(1, "j", PsiTypeConstants.Int),
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testInfixUsageWithTuple2(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiTypeConstants.Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testGeneric(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "t", getPsiTypeFromText("T", targetMethod)),
      new ParameterInfoImpl(-1, "s", getPsiTypeFromText("S", targetMethod)))
    doTest(null, "foo", "T", Seq(params))
  }

  def testVarargs(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiTypeConstants.Int),
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true"),
      new ParameterInfoImpl(1, "strs", new PsiEllipsisType(getPsiTypeFromText("String", targetMethod)))
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testVarargsRemove(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiTypeConstants.Int),
      new ParameterInfoImpl(-1, "b", PsiTypeConstants.Boolean, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testArrayToVarargs(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiTypeConstants.Int),
      new ParameterInfoImpl(2, "b", PsiTypeConstants.Boolean),
      new ParameterInfoImpl(1, "js", new PsiEllipsisType(PsiTypeConstants.Int))
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testNamedAndDefaultArgs(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(2, "s", getPsiTypeFromText("String", targetMethod)),
      new ParameterInfoImpl(3, "b", PsiTypeConstants.Boolean),
      new ParameterInfoImpl(-1, "b2", PsiTypeConstants.Boolean, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonymousFunction(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiTypeConstants.Int), new ParameterInfoImpl(-1, "j", PsiTypeConstants.Int, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testDifferentParamNames0(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "newName", PsiTypeConstants.Int))
    doTest(null, "foo", null, Seq(params))
  }

  def testDifferentParamNames1(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "newName", PsiTypeConstants.Int))
    doTest(null, "foo", null, Seq(params))
  }
}
