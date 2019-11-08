package org.jetbrains.plugins.scala
package codeInsight
package hints

class ScalaMethodChainInlayHintsTest extends InlayHintsTestBase with InlayHintsSettingsTestHelper {

  import Hint.{End => E, Start => S}

  def testChain(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testChainInValDef(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testNonFactoryCall(): Unit = doTest(
    s"""
       |def getList(): List[Int] = ???
       |val x = getList()$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testChainWithInfixCall(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |  .toSeq$S: Seq[String]$E
       |  .toString + ""
     """.stripMargin
  )

  def testChainInParenthesis_1(): Unit = doTest(
    s"""
       |(List(1, 2, 3)
       |  .toSet)$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testChainInParenthesis_3(): Unit = doTest(
    s"""
       |(List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString)$S: String$E
     """.stripMargin
  )

  def testChainInParenthesis_2(): Unit = doTest(
    s"""
       |(List(1, 2, 3)
       |  .map(_ + "")$S: List[String]$E
       |  .toSet)$S: Set[String]$E
       |  .toSeq$S: Seq[String]$E
     """.stripMargin
  )

  def testWithArgumentBlock(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter {
       |    _ > 2
       |  }$S: Set[Int]$E.collect {
       |    case a => a
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArgumentBlock_withNewline(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter {
       |    _ > 2
       |  }$S: Set[Int]$E.collect
       |  {
       |    case a => a
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArguments(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter {
       |    _ > 2
       |  }$S: Set[Int]$E.filter(
       |    _ > 3
       |  ).toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArgumentBlock_withMoreOnTheSameLine(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter {
       |    _ > 2
       |  }.map { e =>
       |    e + 3
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testNoHintsWhenTurnedOf(): Unit = doTest(
    s"""
       |List(1, 2, 3)
       |  .toSeq
       |  .filter(_ > 2)
       |  .toSet
       |  .toString
     """.stripMargin,
    settings = showMethodChainInlayHintsSetting(false)
  )

  def testBoringChainHasNoHints(): Unit = doTest(
    s"""
      |List(1, 2, 3)
      |  .filter(_ > 2)
      |  .filter(_ == 39)
      |  .map(_ + 3)
      |  .filter(_ < 2)
    """.stripMargin
  )

  def testBoringChainHasNoHints_2(): Unit = doTest(
    s"""
       |val test = List(1, 2, 3)
       |test
       |  .filter(_ > 2)
       |  .filter(_ == 39)
       |  .map(_ + 3)
       |  .filter(_ < 2)
    """.stripMargin
  )


  def testChainWithoutLineBreaksHasNoHints(): Unit = doTest(
    s"""
       |List(1, 2, 3).toSeq.filter(_ > 2)
       |  .toSet.toString
     """.stripMargin
  )

  // SCL-16459
  def testShortTypes():Unit = doTest(
    s"""
       |class A {
       |  class BBB {
       |    def ccc: CCC = new CCC
       |  }
       |  class CCC {
       |    def a: A = new A
       |  }
       |  def newB: BBB = new BBB
       |
       |  val b =
       |    (new BBB)$S: BBB$E
       |      .ccc$S: CCC$E
       |      .a$S: A$E
       |}
       |""".stripMargin
  )

  def testWithTypeMismatch(): Unit = doTest(
    s"""
       |val i: Int = List(1, 2, 3)
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  private def doTest(text: String, settings: Setting[_]*): Unit = {
    val allSettings = showObviousTypeSetting(true) +: settings

    withSettings(allSettings) {
      doInlayTest(text)
    }
  }
}
