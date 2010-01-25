package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl

/**
 * Pavel.Fatin, 21.01.2010
 */

class BasicLineMarkerTest extends AbstractLineMarkerTest {

  // TODO enable annotations test
//  def testAnnotations = doTest

  def testArguments = doTest
  def testBody = doTest
  def testCalls = doTest
  def testCurrying = doTest
  def testImplicits = doTest
  def testComplex = doTest
  def testLexerErrorBypass = doTest

  def testGators = {
    doTest

    // TODO make sure somehow that gators (overriding, etc) remain
//    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor.getDocument, myFixture.getProject)
//    assertTrue(markers.get(2).getNavigationHandler != null)
  }
}