package org.jetbrains.plugins.scala.annotator.gutter

class BasicLineMarkerTest extends LineMarkerTestBase {

  // TODO enable annotations test
//  def testAnnotations = doTest

  def testArguments(): Unit = doTest()
  def testBody(): Unit = doTest()
  def testCalls(): Unit = doTest()
  def testCurrying(): Unit = doTest()
  def testImplicits(): Unit = doTest()
  def testComplex(): Unit = doTest()
  def testLexerErrorBypass(): Unit = doTest()

  def testGators(): Unit = {
    doTest()

    // TODO make sure somehow that gators (overriding, etc) remain
//    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor.getDocument, myFixture.getProject)
//    assertTrue(markers.get(2).getNavigationHandler != null)
  }
}