package org.jetbrains.plugins.scala
package lang
package parameterInfo
package functionParameterInfo


class FunctionParameterInfoSimpleTest_since_2_12 extends FunctionParameterInfoTestBase {

  override def getTestDataPath: String =
    s"${super.getTestDataPath}simple/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  def testJavaLibrary(): Unit = doTest()
}

abstract class FunctionParameterInfoSimpleTestBase extends FunctionParameterInfoTestBase {
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

  def testFromPositionalToNaming(): Unit = doTest()

  def testGenericJavaLibrary(): Unit = doTest()

  def testImplicitParameter(): Unit = doTest()

  def testInfixExpression(): Unit = doTest()

  def testInfixTuple(): Unit = doTest()

  def testInfixUnit(): Unit = doTest()

  def testLocal(): Unit = doTest()

  def testNothingExprType(): Unit = doTest()

  def testPositionalAfterNamed(): Unit = doTest()

  def testSimple(): Unit = doTest()

  def testSyntheticParameter(): Unit = doTest()

  def testTypeRefinement(): Unit = doTest()

  def testAliasedMethod(): Unit = doTest()

  def testSeveralParameterLists(): Unit = doTest()

  def testSeveralParameterLists2(): Unit = doTest()

  def testSeveralParameterListsWithImplicit(): Unit = doTest()

  def testDeprecatedOverloads(): Unit = doTest()
}

final class FunctionParameterInfoSimpleTest extends FunctionParameterInfoSimpleTestBase {
  def testDefaultParameterFromSources(): Unit = doTest()

  def testScalaLibrary(): Unit = doTest()
}

final class FunctionParameterInfoSimpleTest_Scala3 extends FunctionParameterInfoSimpleTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testUsingParameter(): Unit = doTest()

  def testPropertyUsing(): Unit = doTest()

  def testPropertyUsingMultipleLists(): Unit = doTest()

  def testPropertyUsingCaretAfter(): Unit = doTest()

  def testPropertyUsingGeneric(): Unit = doTest()

  def testPropertyWithUsingParameterInArgumentList(): Unit = doTest()

  def testPropertyUsingWithQualifier(): Unit = doTest()

  def testSeveralParameterListsWithUsing(): Unit = doTest()

  def testSeveralParameterListsWithUsing2(): Unit = doTest()

  def testSeveralParameterListsWithUsing3(): Unit = doTest()

  def testSeveralParameterListsWithUsing4(): Unit = doTest()

  def testSeveralParameterListsWithUsing5(): Unit = doTest()
}
