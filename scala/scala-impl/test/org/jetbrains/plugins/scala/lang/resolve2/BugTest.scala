package org.jetbrains.plugins.scala.lang.resolve2
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class BugTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "bug/"
  }

  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_2_12

  def testBug1(): Unit = {doTest()}

  //TODO answer?
//  def testIncomplete() {doTest()}

  def testSimplePrivateAccess(): Unit = {doTest()}
  def testPrivateThis(): Unit = {doTest()}
  def testProtectedThis(): Unit = {doTest()}
  def testGetOrElse(): Unit = {doTest()}
  def testAnonymousClassMethods(): Unit = {doTest()}
  //TODO ok
//  def testIntegerEqualiity() {doTest()}
  def testEarlyDefinitionsBefore(): Unit = {doTest()}
  def testFunctionEmptyParamList(): Unit = {doTest()}

  def testCaseClassObjectStaticImport(): Unit = {doTest()}
  def testBufferPlusPlus(): Unit = {doTest()}

  def testCollectionExpression(): Unit = {doTest()}

  def testNamedConstructorParam(): Unit = {doTest()}
  def testNamedConstructorThisParam(): Unit = {doTest()}

  def testValueFunctionOverloading(): Unit = {doTest()}
  def testClassParameterResolve(): Unit = {doTest()}
  def testClassParameterResolveTwo(): Unit = {doTest()}
  def testAnnonymousFunctionUsage(): Unit = {doTest()}
  def testImplicitsApplicability(): Unit = {doTest()}

  def testImplicitChoose(): Unit = {doTest()}

  def testResolveEmpty(): Unit = {doTest()}
  def testOverloadedAction(): Unit = {doTest()}
  def testImplicitsInShapeIgnored(): Unit = {doTest()}

  def testInfixApply(): Unit = {doTest()}
  def testSCL2172(): Unit = {doTest()}
  //TODO failed on buildserver
//  def testSCL2182() {doTest()}
  def testSCL2198(): Unit = {doTest()}
  def testSCL2205(): Unit = {doTest()}
  def testSCL2207(): Unit = {doTest()}
  def testSCL2256(): Unit = {doTest()}
  def testSCL1990(): Unit = {doTest()}
  def testSCL2156(): Unit = {doTest()}
  def testSCL1946(): Unit = {doTest()}
  def testSCL2041(): Unit = {doTest()}
  def testSCL2208(): Unit = {doTest()}
  def testSCL2239(): Unit = {doTest()}
  def testSCL2257(): Unit = {doTest()}
  //TODO Java AnyRef
  //def testSCL2238() {doTest()}

  def testThisTypeSelfType(): Unit = {doTest()}

  def testImplicitsOverloading(): Unit = {doTest()}

  def testSCL21585B(): Unit  = doTest()
  def testSCL21585C(): Unit  = doTest()
  def testSCL21585C2(): Unit = doTest()
  def testSCL21585Cm(): Unit = doTest()
  def testSCL21585D(): Unit  = doTest()
  def testSCL21585E(): Unit  = doTest()
  def testSCL21585E2(): Unit = doTest()
  def testSCL21585E3(): Unit = doTest()
  def testSCL21585E4(): Unit = doTest()
  def testSCL21585E5(): Unit = doTest()
  def testSCL21585F(): Unit  = doTest()
  def testSCL21585G(): Unit  = doTest()
  def testSCL21585H(): Unit  = doTest()
  def testSCL21585I(): Unit  = doTest()

  def testSCL21947A(): Unit = doTest()
  def testSCL21947B(): Unit = doTest()
}
