package org.jetbrains.plugins.scala
package codeInsight
package hints


class ScalaAlignedMethodChainInlayHintsTest extends ScalaMethodChainInlayHintsTestBase {

  import Hint.{End => E, Start => S}
  val empty: String = S + "  " + E

  override protected def doTest(text: String, settings: Setting[_]*): Unit = {
    super.doTest(text, alignMethodChainInlayHints(true) +: uniqueTypesToShowMethodChains(2) +: settings: _*)
  }

  def testChain(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testChainInValDef(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)$S: List[Int]$E
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
       |val x = List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |  .toSeq$S: Seq[String]$E
       |  .toString + ""$S: String$E
     """.stripMargin
  )


  def testChainWithMultiInfixCall(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E map {
       |     _.toString$empty
       |   }$S: Set[String]$E map {
       |     _.toInt$empty
       |   }$S: Set[Int]$E map {
       |     _.toShort$empty
       |   }$S: Set[Short]$E exists {
       |     _ == 3$empty
       |   }$S: Boolean$E
     """.stripMargin
  )


  def testChainWithMultiInfixCall_with_parenthesis(): Unit = doTest(
    s"""
       |val x = List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E map (
       |     _.toString$empty
       |   )$S: Set[String]$E map {
       |     _.toInt$empty
       |   }$S: Set[Int]$E map {
       |     _.toShort$empty
       |   }$S: Set[Short]$E exists {
       |     _ == 3$empty
       |   }$S: Boolean$E
     """.stripMargin
  )

  def testChainInParenthesis_1(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .toSet)$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testChainInParenthesis_3(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString)$S: String$E
     """.stripMargin
  )

  def testChainInParenthesis_2(): Unit = doTest(
    s"""
       |(List(1, 2, 3)$S: List[Int]$E
       |  .map(_ + "")$S: List[String]$E
       |  .toSet)$S: Set[String]$E
       |  .toSeq$S: Seq[String]$E
     """.stripMargin
  )

  def testWithArgumentBlock(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.collect {
       |    case a => a$empty
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  /*
  def testWithArgumentBlock_withLambdaParams(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.collect { a =>
       |    a$empty
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )
*/
  def testWithArgumentBlock_withCases(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.collect { case a =>
       |    a$empty
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArgumentBlock_withCasesWithGuard(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.collect { case a if true =>
       |    a$empty
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArgumentBlock_withNewline(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.collect
       |  {$empty
       |    case a => a$empty
       |  }.toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArguments(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.filter(
       |    _ > 3$empty
       |  ).toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArguments_withAndLambdaParams(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }$S: Set[Int]$E.filter(i =>
       |    i > 3$empty
       |  ).toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testWithArgumentBlock_withMoreOnTheSameLine(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter {$empty
       |    _ > 2$empty
       |  }.map { e => e + 3$empty
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
       |      .newB$S: BBB$E
       |}
       |""".stripMargin
  )

  def testWithTypeMismatch(): Unit = doTest(
    s"""
       |val i: Int = List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .filter(_ > 2)$S: Set[Int]$E
       |  .toSeq$S: Seq[Int]$E
       |  .toString$S: String$E
     """.stripMargin
  )

  def testShowLastTypeInAlignedMode(): Unit = doTest(
    s"""
       |List(1, 2, 3)$S: List[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |  .toSeq$S: Seq[String]$E
       |  .toString$S: String$E
       |""".stripMargin
  )

  def testNoHintsForPackages(): Unit = doTest(
    s"""
       |scala
       |  .collection
       |  .immutable
       |  .Seq(1, 2, 3)$S: Seq[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |""".stripMargin
  )

  def testNoHintsForSingletons(): Unit = doTest(
    s"""
       |scala
       |  .collection
       |  .immutable
       |  .Seq
       |  .empty[Int]$S: Seq[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |""".stripMargin
  )

  def testNoHintsForMultipleObjects(): Unit = doTest(
    s"""
       |object A {
       |  object B {
       |    val seq = Seq(1, 2)
       |  }
       |}
       |A
       |  .B
       |  .seq$S: Seq[Int]$E
       |  .toSet$S: Set[Int]$E
       |  .map(_.toString)$S: Set[String]$E
       |  .toString$S: String$E
       |""".stripMargin
  )
}
