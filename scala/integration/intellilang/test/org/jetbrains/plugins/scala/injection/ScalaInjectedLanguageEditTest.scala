package org.jetbrains.plugins.scala.injection

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class ScalaInjectedLanguageEditTest extends EditorActionTestBase {

  import EditorTestUtil.{CARET_TAG => Caret}
  import org.jetbrains.plugins.scala.util.MultilineStringUtil.{MultilineQuotes => Quotes}

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,$Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |"""

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    |  "a" : 42,
         |    |  $Caret
         |    |  "b" : 23
         |    |}$Quotes.stripMargin
         |"""

    // TODO: we should also test that language is actually injected, and we edit not just a common string
    //  we can't mixin AbstractLanguageInjectionTestCase easily now
    //  wait until we migrate to 192 branch, where tests hierarchy is refactored
    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,$Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |"""

    val after =
      s"""val x =
         |  //language=JSON
         |  $Quotes{
         |    #  "a" : 42,
         |    #  $Caret
         |    #  "b" : 23
         |    #}$Quotes.stripMargin('#')
         |"""

    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedString(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,$Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |"""

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     |  "a" : 42,
         |     |  $Caret
         |     |  "b" : 23
         |     |}$Quotes.stripMargin
         |"""

    checkGeneratedTextAfterEnter(before, after)
  }

  def testInsertMarginCharOnEnterInsideInjectedFileInMultilineInterpolatedStringWithNonDefaultMargin(): Unit = {
    val before =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,$Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |"""

    val after =
      s"""val x =
         |  //language=JSON
         |  s$Quotes{
         |     #  "a" : 42,
         |     #  $Caret
         |     #  "b" : 23
         |     #}$Quotes.stripMargin('#')
         |"""

    checkGeneratedTextAfterEnter(before, after)
  }

}
