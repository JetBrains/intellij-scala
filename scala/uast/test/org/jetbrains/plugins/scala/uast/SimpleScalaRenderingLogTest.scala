package org.jetbrains.plugins.scala.uast

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.RenderLogTestBase

class SimpleScalaRenderingLogTest extends AbstractScalaRenderLogTest {

  def testSimple(): Unit = doTest("SimpleClass.scala")()

  def testClassWithInners(): Unit = doTest("ClassWithInners.scala")()

  def testAnnotations(): Unit = doTest("Annotations.scala")()

  def testAnnotationComplex(): Unit = doTest("AnnotationComplex.scala")()

  def testAnnotationParameters(): Unit = doTest("AnnotationParameters.scala")()

  def testClassAnnotation(): Unit = doTest("ClassAnnotation.scala")()

  def testDefaultImpls(): Unit = doTest("DefaultImpls.scala")()

  def testDefaultParameterValues(): Unit = doTest("DefaultParameterValues.scala")()

  def testIfStatement(): Unit = doTest("IfStatement.scala")()

  def testImports(): Unit = doTest("Imports.scala")()

  def testLocalVariableWithAnnotation(): Unit = doTest("LocalVariableWithAnnotation.scala")()

  def testParametersDisorder(): Unit = doTest("ParametersDisorder.scala")()

  def testQualifiedConstructorCall(): Unit = doTest("QualifiedConstructorCall.scala")()

  def testTypeReferences(): Unit = doTest("TypeReferences.scala")()

  def testAnonymous(): Unit = doTest("Anonymous.scala")()

  def testCalls(): Unit = doTest("CallExpressions.scala")()

  def testMatch(): Unit = doTest("Match.scala")()

  def testLocalFunctions(): Unit = doTest("LocalFunctions.scala")()

  def testComplexSample1(): Unit = doTest("complex/ComplexSample1.scala")()

  def testComplexSample2(): Unit = doTest("complex/ComplexSample2.scala")()

  def testComplexSample3(): Unit = doTest("complex/ComplexSample3.scala")()

  def testLambdas(): Unit = doTest("Lambdas.scala")()
}
