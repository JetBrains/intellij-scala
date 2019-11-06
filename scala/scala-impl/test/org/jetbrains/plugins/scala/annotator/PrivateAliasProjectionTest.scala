package org.jetbrains.plugins.scala.annotator

class PrivateAliasProjectionTest extends AnnotatorLightCodeInsightFixtureTestAdapter {
  def testSCL16525(): Unit = {
    checkTextHasNoErrors(
      """
        |case class MyType(str: String)
        |trait Producer[A] {
        |  private type R = Option[A]
        |  protected def produce(str: String): A
        |  def p(str: String): R = Some(produce(str))
        |}
        |object MyProducer extends Producer[MyType] {
        |  override def produce(str: String): MyType = MyType(str)
        |}
        |object Main {
        |  def foo(x: Option[MyType]): Unit = println(x)
        |  def bar(): Unit = foo(MyProducer.p("asdf")/* this is where IDEA reports an error*/)
        |}
      """.stripMargin)
  }
}
