package org.jetbrains.plugins.scala.lang.resolve2

/**
 * @author Alexander Podkhalyuzin
 */

class OverloadingTest extends ResolveTestBase {
  override def getTestDataPath: String = {
    super.getTestDataPath + "overloading/"
  }

  def testAmbiguos = doTest
  def testCanConformsWeak = doTest
  def testCannotChoose = doTest
  def testCantConformsWeak = doTest
  def testDefault = doTest
  def testDefaultIgnored = doTest
  def testDerivedMoreSpecific = doTest
  def testDifferent = doTest
  def testFewWeakConforms = doTest
  def testImplicitApplied = doTest
  def testImplicitIgnored = doTest
  def testImplicitVSLiteralNarrowing = doTest
  def testImplicitVSValueDiscarding = doTest
  def testImplicitVSWeak = doTest
  def getMoreSpecificRight = doTest
  def testNaming = doTest
  def testNoLiteralNarrowing = doTest
  def testNoOveloadingScope = doTest
  def testNoVAlueDiscarding = doTest
  def testObjectFunction = doTest
  def testSameScoreForMoreSpecific = doTest
  def testSimple = doTest
  def testTooMuchImplicits = doTest
  def testWeakResolve = doTest
}