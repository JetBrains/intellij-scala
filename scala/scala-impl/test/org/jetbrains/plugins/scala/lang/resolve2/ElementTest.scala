package org.jetbrains.plugins.scala.lang.resolve2

class ElementTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "element/"
  }

  def testCaseClass(): Unit = doTest()
  def testCaseObject(): Unit = doTest()
  def testClass(): Unit = doTest()
  def testClassParameter(): Unit = doTest()
  def testClassParameterValue(): Unit = doTest()
  def testClassParameterVariable(): Unit = doTest()
  def testObject(): Unit = doTest()
  def testTrait(): Unit = doTest()
  def testFunctionDefinition(): Unit = doTest()
  def testFunctionParameter(): Unit = doTest()
  def testFunctionParameterClause(): Unit = doTest()
  def testMacroDefinition(): Unit = doTest()
  def testNamedParameter(): Unit = doTest()
  def testConstructorParameter(): Unit = doTest()
  def testTypeAlias(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterClass = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterFunction = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testTypeParameterTrait = doTest
  def testBinding(): Unit = doTest()
  def testValues(): Unit = doTest()
  def testStatementForValue(): Unit = doTest()
  def testStatementForValues(): Unit = doTest()
  def testStatementForBinding(): Unit = doTest()
  def testStatementForAssignment(): Unit = doTest()
  def testFunctionExpressionParameter(): Unit = doTest()
  def testCaseClauseParameter(): Unit = doTest()
  def testCaseClauseNamed(): Unit = doTest()
  def testCaseClauseBinding(): Unit = doTest()

  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testPackage = doTest
  //TODO answer? classof, it's internal compiler error, should be resolved, but another error in annotator
//  def testPackageObject = doTest
}