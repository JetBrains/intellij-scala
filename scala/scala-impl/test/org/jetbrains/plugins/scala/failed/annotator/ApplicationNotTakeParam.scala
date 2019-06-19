package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ApplicationNotTakeParam extends ScalaLightCodeInsightFixtureTestAdapter {

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
}

@Category(Array(classOf[PerfCycleTests]))
class ApplicationNotTakeParam_Failing extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

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

  def testSCL13689(): Unit = {
    checkTextHasNoErrors(
      """
        |class parent {
        |  def abc[T]: T = ???
        |}
        |
        |object foo extends parent {
        |  def abc: Nothing = ???
        |}
        |
        |object bar {
        |  foo.abc[Int]
        |}
      """.stripMargin)
  }
}
