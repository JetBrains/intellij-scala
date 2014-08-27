package org.jetbrains.plugins.scala
package refactoring.changeSignature

import com.intellij.psi.{PsiEllipsisType, PsiType}
import com.intellij.refactoring.changeSignature.ParameterInfoImpl

/**
 * Nikolay.Tropin
 * 2014-08-14
 */
class ChangeSignatureFromJavaTest extends ChangeSignatureFromJavaTestBase {

  def testStaticMethod() = {
    val newParams = Array(
      new ParameterInfoImpl(0, "ii", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"))
    doTest(null, "bar", null, newParams)
  }

  def testInstanceMethod() = {
    val newParams = Array(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
      )
    doTest(null, "bar", null, newParams)
  }

  def testOverriders() = {
    val newParams = Array(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
    )
    doTest(null, "bar", "boolean", newParams)
  }

  def testOverriderInAnonClass() = {
    val newParams = Array(
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0, "ii", PsiType.INT)
    )
    doTest(null, "bar", "boolean", newParams)
  }

  def testParameterlessOverriders() = {
    doTest(null, "bar", "String", Array())
  }

  def testParameterlessOverriders2() = {
    doTest(null, "bar", "String", Array(new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")))
  }

  def testInfixUsage() = {
    doTest(null, "print", null, Array(new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")))
  }

  def testInfixUsage2() = {
    doTest(null, "print", null, Array(new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")))
  }

  def testInfixUsageWithTuple(): Unit = {
    doTest(null, "foo", null, Array(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(1, "j", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")
    ))
  }

  def testInfixUsageWithTuple2(): Unit = {
    doTest(null, "foo", null, Array(new ParameterInfoImpl(0, "i", PsiType.INT)))
  }

  def testGeneric(): Unit = {
    doTest(null, "foo", "T", Array(
      new ParameterInfoImpl(0, "t", getPsiTypeFromText("T", targetMethod)),
      new ParameterInfoImpl(-1, "s", getPsiTypeFromText("S", targetMethod))))
  }

  def testVarargs(): Unit = {
    doTest(null, "foo", null, Array(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(1, "strs", new PsiEllipsisType(getPsiTypeFromText("String", targetMethod)))
    ))
  }

  def testVarargsRemove(): Unit = {
    doTest(null, "foo", null, Array(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true")
    ))
  }

  def testArrayToVarargs(): Unit = {
    doTest(null, "foo", null, Array(
      new ParameterInfoImpl(0, "i", PsiType.INT),
      new ParameterInfoImpl(2, "b", PsiType.BOOLEAN),
      new ParameterInfoImpl(1, "js", new PsiEllipsisType(PsiType.INT))
    ))
  }

}
