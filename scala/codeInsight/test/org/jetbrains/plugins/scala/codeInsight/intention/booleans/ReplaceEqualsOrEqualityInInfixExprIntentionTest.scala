package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ReplaceEqualsOrEqualityInInfixExprIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.replace.equals.or.equality.in.infix.expression")

  def testReplaceQuality(): Unit = {
    val text = s"if (a ==$CARET b) return"
    val resultText = s"if (a ${CARET}equals b) return"

    doTest(text, resultText)
  }

  def testReplaceQuality2(): Unit = {
    val text = s"if (a ${CARET}equals false) return"
    val resultText = s"if (a $CARET== false) return"

    doTest(text, resultText)
  }
}
