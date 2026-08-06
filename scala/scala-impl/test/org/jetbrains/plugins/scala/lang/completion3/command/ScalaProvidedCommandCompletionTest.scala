package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import org.junit.Test

/**
 * Test platform command providers registered for Scala, such as line/block comment
 */
final class ScalaProvidedCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val BlockCommentPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Comment with block")
  private val ExplainRegexPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Explain regular expression")
  private val FileStructurePredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Go to members")
  private val LineCommentPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Comment with line")
  private val LiveTemplatesPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Show live templates")
  private val UncommentPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Uncomment")

  @Test
  def commentElementByLine(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    def inner(): Unit = {
         |      val a: String = "1"
         |    }.$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      """
        |object Test {
        |  def main(args: Array[String]): Unit = {
        |//    def inner(): Unit = {
        |//      val a: String = "1"
        |//    }
        |  }
        |}
        |""".stripMargin,
    predicate = LineCommentPredicate
  )

  @Test
  def noCompletionForCommentSingleLineElementByLine(): Unit = checkNoCommandCompletion(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    def inner(): Unit = {
         |      val a: String = "1".$CARET
         |    }
         |  }
         |}
         |""".stripMargin,
    predicate = LineCommentPredicate
  )

  @Test
  def commentElementByBlock(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    def inner(): Unit = {
         |      val a: String = "1"
         |    }.$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      """
        |object Test {
        |  def main(args: Array[String]): Unit = {
        |    /*def inner(): Unit = {
        |      val a: String = "1"
        |    }*/
        |  }
        |}
        |""".stripMargin,
    predicate = BlockCommentPredicate
  )

  @Test
  def commentSingleLineElementByBlock(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    def inner(): Unit = {
         |      val a: String = "1".$CARET
         |    }
         |  }
         |}
         |""".stripMargin,
    resultText =
      """
        |object Test {
        |  def main(args: Array[String]): Unit = {
        |    def inner(): Unit = {
        |      /*val a: String = "1"*/
        |    }
        |  }
        |}
        |""".stripMargin,
    predicate = BlockCommentPredicate
  )

  @Test
  def noCompletionUncommentElementByLine(): Unit = checkNoCommandCompletion(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |//    def inner(): Unit = {
         |//      val a: String = "1"
         |//    }.$CARET
         |  }
         |}
         |""".stripMargin,
    predicate = UncommentPredicate
  )

  @Test
  def uncommentElementByBlock(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    /*def inner(): Unit = {
         |      val a: String = "1"
         |    }*/.$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      """
        |object Test {
        |  def main(args: Array[String]): Unit = {
        |    def inner(): Unit = {
        |      val a: String = "1"
        |    }
        |  }
        |}
        |""".stripMargin,
    predicate = UncommentPredicate
  )

  @Test
  def showLiveTemplates(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def test(): Unit = {
         |    .$CARET
         |  }
         |}""".stripMargin,
    predicate = LiveTemplatesPredicate
  )

  @Test
  def showFileStructure(): Unit = doCommandCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def test(): Unit = {
         |    val x: Int = .$CARET
         |  }
         |}""".stripMargin,
    predicate = FileStructurePredicate
  )

  // FIXME(IJPL-247033): DirectIntentionCommandProvider now runs annotators in batch mode
  //                     so our fixes are ignored as they are not added as `batch` or `universal` fixes
  /*
  @Test
  def redCode(): Unit = doCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val i: Int = 1L..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  val i: Long = 1L
         |}""".stripMargin,
    predicate = lookupStringStartsWith(_, "Change type 'Int' to 'Long'"),
    expectedIcon = AllIcons.Actions.QuickfixBulb,
  )
  */

  // FIXME(IJPL-247033): DirectIntentionCommandProvider now runs annotators in batch mode
  //                     so our fixes are ignored as they are not added as `batch` or `universal` fixes
  // TODO: seems to be working in unit tests only (see `if (isUnitTestMode)` in `ScalaImportElementFix`)
  /*
  @Test
  def redCodeImport(): Unit = doCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val list = new ArrayList..$CARET[Int]()
         |}""".stripMargin,
    resultText =
      s"""import java.util
         |
         |object Test {
         |  val list = new util.ArrayList[Int]()
         |}""".stripMargin,
    predicate = lookupStringStartsWith(_, "Import 'java.util.ArrayList'"),
    expectedIcon = AllIcons.Actions.QuickfixBulb,
  )
  */

  @Test
  def explainRegexOnDotRString(): Unit = doCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val regex = "abc".$CARET.r
         |}""".stripMargin,
    predicate = ExplainRegexPredicate,
    finishLookup = false
  )

  @Test
  def explainRegexOnLargerRegexPattern(): Unit = doCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val regex = "[a-z]+\\\\d".$CARET.r
         |}""".stripMargin,
    predicate = ExplainRegexPredicate,
    finishLookup = false
  )

  @Test
  def explainRegexOnRegexInPatternCompile(): Unit = doCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val regex = java.util.regex.Pattern.compile("[a-z]+\\\\d".$CARET)
         |}""".stripMargin,
    predicate = ExplainRegexPredicate,
    finishLookup = false
  )

  @Test
  def noExplainRegexForRegularString(): Unit = checkNoCommandCompletion(
    fileText =
      s"""object Test {
         |  val s = "abc".$CARET
         |}""".stripMargin,
    predicate = ExplainRegexPredicate
  )

  @Test
  def noExplainRegexOnIntegerLiteral(): Unit = checkNoCommandCompletion(
    fileText =
      s"""object Test {
         |  val x = 42.$CARET
         |}""".stripMargin,
    predicate = ExplainRegexPredicate
  )

  @Test // TODO: could be useful to support this
  def noExplainRegexOnDotRCall(): Unit = checkNoCommandCompletion(
    fileText =
      s"""object Test {
         |  def test(): Unit = {
         |    val regex = "abc".r.$CARET
         |  }
         |}""".stripMargin,
    predicate = ExplainRegexPredicate
  )

  @Test
  def noExplainRegexOnPatternCompileCall(): Unit = checkNoCommandCompletion(
    fileText =
      s"""object Test {
         |  val regex = java.util.regex.Pattern.compile("[a-z]+\\\\d").$CARET
         |}""".stripMargin,
    predicate = ExplainRegexPredicate
  )
}
