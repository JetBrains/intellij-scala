package org.jetbrains.plugins.scala.debugger.stepOver

import org.jetbrains.plugins.scala.debugger.ScalaVersion_2_11


/**
 * @author Nikolay.Tropin
 */

class StepOverCaseClausesTest extends StepOverCaseClausesTestBase with ScalaVersion_2_11

abstract class StepOverCaseClausesTestBase extends StepOverTestBase {
  def testSimple(): Unit = {
    testStepThrough(
      """object Sample {
        |  def main (args: Array[String]){
        |    ""
        |    List(1) match {
        |      case Seq(2) =>
        |      case Seq(3) =>
        |      case IndexedSeq(5) =>
        |      case IndexedSeq(6) =>
        |      case Seq(1) =>
        |      case Seq(7) =>
        |      case Seq(8) =>
        |    }
        |  }
        |}
      """, Seq(2, 3, 4, 5, 6, 8, 1))
  }

  def testMultilineExpr(): Unit = {
    testStepThrough(
      """object Sample {
        |  def main (args: Array[String]){
        |    ""
        |    Seq(2, 3)
        |      .map(_ - 1)
        |    match {
        |      case IndexedSeq(1, 2) =>
        |      case IndexedSeq(2, 3) =>
        |      case Seq(2) =>
        |      case Seq(1, _) =>
        |      case Seq(3) =>
        |    }
        |  }
        |}
      """, Seq(2, 3, 4, 6, 8, 9, 1))
  }

  def testSkipStoreResult(): Unit = {
    testStepThrough(
    """object Sample {
      |  def main (args: Array[String]){
      |    ""
      |    val z = Seq(1, 2) match {
      |      case Seq(1, _) =>
      |        foo()
      |        fee()
      |      case _ =>
      |        fee()
      |        foo()
      |    }
      |    println(z)
      |  }
      |
      |  def foo() = "foo"
      |  def fee() = "fee"
      |}""", Seq(2, 3, 4, 5, 6, 11)
    )
  }

  def testPartialFun(): Unit = {
    testStepThrough(
      """object Sample {
        |  def main (args: Array[String]){
        |    ""
        |    val z = Seq(Some(1), Some(2), Some(3)) collect {
        |      case Some(1) =>
        |        foo()
        |        fee()
        |      case Some(2) =>
        |        fee()
        |        foo()
        |    }
        |    println(z)
        |  }
        |
        |  def foo() = "foo"
        |  def fee() = "fee"
        |}
        |""", Seq(4, 5, 6, 3, 4, 7, 8, 9, 3, 4, 7, 3, 11), startBreakpoint = (4, -1)
    )
  }

  def testComplexPattern(): Unit = {
    testStepThrough(
    """object Sample {
      |  def main (args: Array[String]){
      |    ""
      |    val z = Seq(left(1), left(2)) match {
      |      case Seq(Right("1")) =>
      |        foo()
      |        fee()
      |      case Left(Seq(Some(x))) and Left(Seq(None)) =>
      |        fee()
      |        foo()
      |      case Left(Seq(_)) and Left(Seq(Some(2))) =>
      |        fee()
      |        foo()
      |    }
      |    println(z)
      |  }
      |
      |  def foo() = "foo"
      |  def fee() = "fee"
      |  def left(i: Int): Either[Seq[Option[Int]], String] = Left(Seq(Some(i)))
      |
      |  object and {
      |    def unapply(s: Seq[_]): Option[(Any, Any)] = {
      |      s match {
      |        case Seq(x, y) => Some((x, y))
      |        case _ => None
      |      }
      |    }
      |  }
      |}
      |
    """, Seq(2, 3, 4, 7, 10, 11, 12, 14)
    )
  }

  def testNestedMatch(): Unit = {
    testStepThrough(
    """object Sample {
      |  def main (args: Array[String]){
      |    ""
      |    val z = Seq(left(1), left(2)) match {
      |      case Seq(Left(Seq(Some(1))), x) => x match {
      |        case Left(Seq(None)) =>
      |          fee()
      |          foo()
      |        case Left(Seq(Some(2))) =>
      |          fee()
      |          foo()
      |      }
      |      case _ =>
      |    }
      |    println(z)
      |  }
      |
      |  def foo() = "foo"
      |  def fee() = "fee"
      |  def left(i: Int): Either[Seq[Option[Int]], String] = Left(Seq(Some(i)))
      |}
      |""", Seq(2, 3, 4, 5, 8, 9, 10, 14)
    )
  }
}

//object Sampl {
//  def main (args: Array[String]){
//    ""
//    val z = Seq(left(1), left(2)) match {
//      case Seq(Left(Seq(Some(1))), x) => x match {
//        case Left(Seq(None)) =>
//          fee()
//          foo()
//        case Left(Seq(Some(2))) =>
//          fee()
//          foo()
//      }
//    }
//    println(z)
//  }
//
//  def foo() = "foo"
//  def fee() = "fee"
//  def left(i: Int): Either[Seq[Option[Int]], String] = Left(Seq(Some(i)))
//}
