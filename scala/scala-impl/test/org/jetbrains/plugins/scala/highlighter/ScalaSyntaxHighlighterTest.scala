package org.jetbrains.plugins.scala.highlighter

import junit.framework.TestCase

class ScalaSyntaxHighlighterTest extends TestCase {

  def testScalaSyntaxHighlighterObjectFieldsAreSuccessfullyInitialized(): Unit =
    ScalaSyntaxHighlighter.toString
}