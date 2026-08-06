package org.jetbrains.plugins.scala
package lang
package completeStatement

class ScalaCompleteForStatementTest extends ScalaCompleteStatementTestBase {

  def testOnlyForKeywordWithSpace(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for $CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {}
         |  }
         |}
      """.stripMargin
  )

  def testOnlyForKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithOpenParen(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithOpenBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndOneEnumeratorInParens(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET) {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndOneEnumeratorInBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10$CARET } {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10 } {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndOneEnumeratorInBraces2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10$CARET
         |    } {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |      $CARET
         |    } {
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndMultipleEnumeratorsInParens(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET; j <- 1 to 10) {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10; j <- 1 to 10) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndMultipleEnumeratorsInBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10$CARET; j <- 1 to 10 } {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10; j <- 1 to 10 } {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithBodyAndMultipleEnumeratorsInBraces2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10$CARET
         |      j <- 1 to 10
         |    } {
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |      $CARET
         |      j <- 1 to 10
         |    } {
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithEnumsAndOpenBodyBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithEnumsInBracesAndOpenBodyBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10 } {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10 } {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithEnumsInBracesAndOpenBodyBrace2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |    } {$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |    } {
         |      $CARET
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInParensAndNonemptyBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET) {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10) {
         |      $CARET
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInBracesAndNonemptyBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10$CARET } {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10 } {
         |      $CARET
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInBracesAndNonemptyBody2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10$CARET
         |    } {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |      $CARET
         |    } {
         |      println()
         |    }
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInParensNoBodyNoClosingParen(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for (i <- 1 to 10$CARET) {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInBracesNoBodyNoClosingBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for { i <- 1 to 10$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {i <- 1 to 10$CARET} {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInBracesNoBodyNoClosingBrace2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1$CARET to 10
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |      $CARET
         |    } {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithOneEnumInBracesNoBodyNoClosingBrace3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10$CARET
         |
         |    println()
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {
         |      i <- 1 to 10
         |      $CARET
         |    }
         |
         |    println()
         |  }
         |}
      """.stripMargin
  )

  def testForWithEmptyParens(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for ()$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for ($CARET) {}
         |  }
         |}
      """.stripMargin
  )

  def testForWithEmptyBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def method() {
         |    for {$CARET}
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def method() {
         |    for {$CARET} {}
         |  }
         |}
      """.stripMargin
  )

  // SCL-23021
  def testForWithEnumInBracesAndBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def list(size: Int) = List.fill(size)(1)
         |  def method() {
         |    for {
         |      x <- list(2)$CARET
         |    } yield ()
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def list(size: Int) = List.fill(size)(1)
         |  def method() {
         |    for {
         |      x <- list(2)
         |      $CARET
         |    } yield ()
         |  }
         |}
      """.stripMargin
  )

  // SCL-23021
  def testForWithEnumInBracesAndNoBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class B {
         |  def list(size: Int) = List.fill(size)(1)
         |  def method() {
         |    for {
         |      x <- list(2)$CARET
         |    }
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class B {
         |  def list(size: Int) = List.fill(size)(1)
         |  def method() {
         |    for {
         |      x <- list(2)
         |      $CARET
         |    } {}
         |  }
         |}
      """.stripMargin
  )
}
