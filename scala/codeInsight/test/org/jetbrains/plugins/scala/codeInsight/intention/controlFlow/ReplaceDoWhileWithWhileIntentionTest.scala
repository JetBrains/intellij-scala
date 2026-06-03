package org.jetbrains.plugins.scala
package codeInsight
package intention
package controlFlow

final class ReplaceDoWhileWithWhileIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName = ScalaCodeInsightBundle.message("family.name.replace.do.while.with.while")

  def testReplaceDoWhile1(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
        |  def f {
         |    <caret>do {
         |       print("")
         |      //comment
         |    } while(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    //comment
         |    while (flag) {
         |      print("")
         |      //comment
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile2(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    do<caret> {
         |      print("")
         |      //comment
         |    } while(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    //comment
         |    while (flag) {
         |      print("")
         |      //comment
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile3(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    do {
         |      print("")
         |      //comment
         |    } <caret>while(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    //comment
         |    while (flag) {
         |      print("")
         |      //comment
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile4(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    do {
         |      print("")
         |      //comment
         |    } while<caret>(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    //comment
         |    while (flag) {
         |      print("")
         |      //comment
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile5(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    if (true)
         |      do {
         |        print("")
         |      } while<caret>(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    if (true) {
         |      print("")
         |      while (flag) {
         |        print("")
         |      }
         |    }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile6(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    <caret>do print("")
         |    while(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    while (flag) print("")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile7(): Unit = {
    val text =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    <caret>do print("") while(flag)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  val flag: Boolean
         |
         |  def f {
         |    print("")
         |    while (flag) print("")
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile8(): Unit = {
    val text =
      s"""class X {
         |  1 match {
         |  case 1 =>
         |    <caret>do {
         |      print("")
         |    } while (true)
         |  }
         |}""".stripMargin
    val resultText =
      s"""class X {
         |  1 match {
         |    case 1 =>
         |      print("")
         |      while (true) {
         |        print("")
         |      }
         |  }
         |}""".stripMargin

    doTest(text, resultText)
  }

  def testReplaceDoWhile9(): Unit = {
    val text =
      """do println("")
        |while (true)""".stripMargin
    val resultText =
      """println("")
        |while (true) println("")""".stripMargin

    doTest(text, resultText)
  }


}
