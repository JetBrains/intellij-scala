package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.{PsiEllipsisType, PsiMember, PsiMethod, PsiType}
import com.intellij.refactoring.changeSignature._
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 2014-08-14
 */
class ChangeSignatureFromJavaTest extends ChangeSignatureTestBase {

  override def folderPath: String = baseRootPath + "changeSignature/fromJava/"

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
    val element = new JavaChangeSignatureHandler().findTargetMember(getFileAdapter, getEditorAdapter)
    assertTrue("<caret> is not on method name", element.isInstanceOf[PsiMethod])
    element.asInstanceOf[PsiMethod]
  }

  def testStaticMethod() = {
    val params = Seq(
      new ParameterInfoImpl(0, "ii", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"))
    doTest(null, "bar", null, Seq(params))
  }

  def testInstanceMethod() = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
    )
    doTest(null, "bar", null, Seq(newParams))
  }

  def testOverriders() = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
    )
    doTest(null, "bar", "boolean", Seq(newParams))
  }

  def testOverriderInAnonClass() = {
    val newParams = Seq(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
    )
    doTest(null, "bar", "boolean", Seq(newParams))
  }

  def testParameterlessOverriders() = {
    doTest(null, "bar", null, Seq(Seq.empty))
  }

  def testParameterlessOverriders2() = {
    val params = Seq(new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"))
    doTest(null, "bar", null, Seq(params))
  }

  def testInfixUsage() = {
    val params = Seq(new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"))
    doTest(null, "print", null, Seq(params))
  }

  def testInfixUsage2() = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"))
    doTest(null, "print", null, Seq(params))
  }

  def testInfixUsageWithTuple(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(1, "j", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testInfixUsageWithTuple2(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiType.INT))
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
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(1, "strs", new PsiEllipsisType(getPsiTypeFromText("String", targetMethod)))
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testVarargsRemove(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testArrayToVarargs(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(2, "b", PsiType.BOOLEAN),
      new ParameterInfoImpl(1, "js", new PsiEllipsisType(PsiType.INT))
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testNamedAndDefaultArgs(): Unit = {
    val params = Seq(
      new ParameterInfoImpl(2, "s", getPsiTypeFromText("String", targetMethod)),
      new ParameterInfoImpl(3, "b", PsiType.BOOLEAN),
      new ParameterInfoImpl(-1, "b2", PsiType.BOOLEAN, "true")
    )
    doTest(null, "foo", null, Seq(params))
  }

  def testAnonymousFunction(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "i", PsiType.INT), new ParameterInfoImpl(-1, "j", PsiType.INT, "0"))
    doTest(null, "foo", null, Seq(params))
  }

  def testDifferentParamNames(): Unit = {
    val params = Seq(new ParameterInfoImpl(0, "newName", PsiType.INT))
    doTest(null, "foo", null, Seq(params))
  }
}
