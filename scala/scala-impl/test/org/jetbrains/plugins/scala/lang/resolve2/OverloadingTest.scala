package org.jetbrains.plugins.scala.lang.resolve2

class OverloadingTest extends ResolveTestBase {
  override def folderPath: String = {
    super.folderPath + "overloading/"
  }

  def testAmbiguos(): Unit = doTest()
  def testCanConformsWeak(): Unit = doTest()
  def testCannotChoose(): Unit = doTest()
  def testCantConformsWeak(): Unit = doTest()
  def testDefault(): Unit = doTest()
  def testDefaultIgnored(): Unit = doTest()
  def testDerivedMoreSpecific(): Unit = doTest()
  def testDifferent(): Unit = doTest()
  def testFewWeakConforms(): Unit = doTest()
  def testImplicitApplied(): Unit = doTest()
  def testImplicitIgnored(): Unit = doTest()
  def testImplicitVSLiteralNarrowing(): Unit = doTest()
  def testImplicitVSValueDiscarding(): Unit = doTest()
  def testImplicitVSWeak(): Unit = doTest()
  def testMoreSpecificRight(): Unit = doTest()
  def testNaming(): Unit = doTest()
  def testNoLiteralNarrowing(): Unit = doTest()
  def testNoOveloadingScope(): Unit = doTest()
  def testNoVAlueDiscarding(): Unit = doTest()
  def testObjectFunction(): Unit = doTest()
  def testSameScoreForMoreSpecific(): Unit = doTest()
  def testSimple(): Unit = doTest()
  def testTooMuchImplicits(): Unit = doTest()
  def testWeakResolve(): Unit = doTest()
  def testWrong(): Unit = {doTest()}

  //SCL-12375
  def testApplyFromImplicit(): Unit = doTest()
  //SCL-12452
  def testApplyFromImplicit2(): Unit = doTest()
}