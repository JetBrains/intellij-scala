package org.jetbrains.plugins.scala
package lang
package parameterInfo
package functionParameterInfo


class FunctionParameterInfoSimpleTest_since_2_12 extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}simple/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_12

  def testJavaLibrary(): Unit = doTest()
}


class FunctionParameterInfoSimpleTest extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}simple/"
  
  def testAnnotation(): Unit = doTest()

  def testProperty(): Unit = doTest()

  def testPropertyInArgumentList(): Unit = doTest()

  def testPropertyWithImplicitParameterInArgumentList(): Unit = doTest()

  def testPropertyGeneric(): Unit = doTest()

  def testPropertyInBlockArg(): Unit = doTest()

  def testPropertyCaretAfter(): Unit = doTest()

  def testPropertyInInfix(): Unit = doTest()

  def testPropertyInInfixTuple(): Unit = doTest()

  def testPropertyWithQualifier(): Unit = doTest()

  def testDefaultParameter(): Unit = doTest()

  def testDefaultParameterFromSources(): Unit = doTest()

  def testFromPositionalToNaming(): Unit = doTest()

  def testGenericJavaLibrary(): Unit = doTest()

  def testImplicitParameter(): Unit = doTest()

  def testInfixExpression(): Unit = doTest()

  def testInfixTuple(): Unit = doTest()

  def testInfixUnit(): Unit = doTest()

  def testLocal(): Unit = doTest()

  def testNothingExprType(): Unit = doTest()

  def testPositionalAfterNamed(): Unit = doTest()

  def testScalaLibrary(): Unit = doTest()

  def testSimple(): Unit = doTest()

  def testSyntheticParameter(): Unit = doTest()

  def testTypeRefinement(): Unit = doTest()

  def testAliasedMethod(): Unit = doTest()

  def testSeveralParameterLists(): Unit = doTest()

  def testSeveralParameterLists2(): Unit = doTest()

  def testSeveralParameterListsWithImplicit(): Unit = doTest()

  def testDeprecatedOverloads(): Unit = doTest()
}
