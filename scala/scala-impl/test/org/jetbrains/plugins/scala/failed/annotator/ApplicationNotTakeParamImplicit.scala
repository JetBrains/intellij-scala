package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ApplicationNotTakeParamImplicit extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL10352(): Unit = {
    checkTextHasNoErrors(
      """
        |abstract class BlockModel[T <: Block[_]] (implicit c: scala.reflect.ClassTag[T]){}
        |
        |class Block[R <: BlockModel[_]](implicit val repr: R) {???}
        |
        |abstract class Screen[R <: BlockModel[_]](override implicit val repr: R) extends Block[R]  {}
        |
        |object TabsDemoScreen {
        |  implicit object TabsDemoScreenModel extends BlockModel[TabsDemoScreen] {
        |  }
        |}
        |class TabsDemoScreen extends Screen[TabsDemoScreen.TabsDemoScreenModel.type] {}
      """.stripMargin)
  }

  def testSCL10902(): Unit = {
    checkTextHasNoErrors(
      """
        |object Test extends App {
        | class A { def apply[Z] = 42 }
        | def create = new A
        |
        | create[String]
        |}
      """.stripMargin)
  }

  def testSCL12447(): Unit = {
    checkTextHasNoErrors(
      """
        |1.##()
      """.stripMargin)
  }

  def testSCL13211(): Unit = {
    checkTextHasNoErrors(
      """
        |object Glitch {
        |  object myif {
        |    def apply(cond: Boolean)(block: => Unit): MyIf = {
        |      new MyIf(cond)
        |    }
        |  }
        |  class MyElseIfClause(val cond : Boolean, _block: => Unit){
        |    def unary_! : MyElseIfClause = new MyElseIfClause(!cond, _block)
        |    def block = _block
        |  }
        |
        |  implicit class MyElseIfClauseBuilder(cond : Boolean){
        |    def apply(block : => Unit) : MyElseIfClause = new MyElseIfClause(cond, block)
        |  }
        |
        |  class MyIf (prevCond: Boolean) {
        |    def myelseif (clause : MyElseIfClause) : MyIf = privMyElseIf(clause.cond)(clause.block)
        |    private def privMyElseIf (cond : Boolean)(block: => Unit) : MyIf = {
        |      new MyIf(prevCond || cond)
        |    }
        |    def myelse (block: => Unit) {
        |      val cond = !prevCond
        |    }
        |  }
        |
        |  myif(true) {
        |  } myelseif (!false) { //Cannot resolve symbol !
        |  } myelse {
        |  }
        |}
      """.stripMargin)
  }
}
