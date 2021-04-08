package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author anton.yalyshev
  * @since 14.04.16.
  */
class ApplyConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL13046(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait Test {
         |  type Repr
         |}
         |
         |object Test {
         |  def getRepr(t: Test): t.Repr = ???
         |
         |  def whatever(t: Test): Unit = {
         |    val value: t.Repr = getRepr(t)
         |  }
         |}
      """.stripMargin)
  }

  def testSCL13730(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Repro {
         |  type Handler[IN, OUT] = IN => OUT
         |  trait HandlerManager[OUT] {
         |    def handlerFor[T](msg: T): Option[Handler[_, OUT]]
         |  }
         |
         |  object TypedExtractor {
         |    def unapply[T, OUT](msg: T)(implicit handlers: HandlerManager[OUT]): Option[OUT] = {
         |      (handlers handlerFor msg) map {
         |        e => e.asInstanceOf[Handler[T, OUT]](msg)
         |      }
         |    }
         |  }
         |
         |  def test(implicit handlers: HandlerManager[String]): PartialFunction[Any, String] = {
         |    case TypedExtractor(stringOutput) => stringOutput  // last 'stringOutput is red'
         |  }
         |}
      """.stripMargin)
  }
}
