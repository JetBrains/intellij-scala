package org.jetbrains.plugins.scala.lang.completion3.command

import org.junit.Test

/**
 * Test platform command providers registered for Scala, such as line/block comment
 */
final class ScalaProvidedCommandCompletionTest extends ScalaCommandCompletionTestBase {
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
    predicate = lookupStringContains(_, "Comment with line")
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
    predicate = lookupStringContains(_, "Comment with line")
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
    predicate = lookupStringContains(_, "Comment with block")
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
    predicate = lookupStringContains(_, "Comment with block")
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
    predicate = lookupStringContains(_, "Uncomment")
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
    predicate = lookupStringContains(_, "Uncomment")
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
    predicate = lookupStringContains(_, "Show live templates")
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
    predicate = lookupStringContains(_, "Go to members")
  )
}
