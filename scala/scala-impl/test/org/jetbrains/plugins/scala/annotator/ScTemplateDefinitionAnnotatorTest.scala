package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScTemplateDefinitionAnnotatorTest
  extends ScalaLightCodeInsightFixtureTestCase
    with ScalaHighlightingTestLike {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testTraitPassingConstructorParameters(): Unit =
    assertMessagesText(
      """
        |trait Foo(x: Int)
        |trait Bar extends Foo(123)
        |
        |trait Baz
        |trait Qux extends Baz with Foo(123)
        |""".stripMargin,
      """Error(Foo(123),Trait Bar may not call constructor of Foo)
        |Error(Foo(123),Trait Qux may not call constructor of Foo)
        |""".stripMargin
    )

  def testDontCallConstructorTwice(): Unit =
    assertMessagesText(
      """trait Foo(x: Int)
        |class F extends Foo(123)
        |case class Bar() extends F with Foo
        |""".stripMargin,
      //TODO: remove when SCL-23134 is fixed
      """Error(Foo,Unspecified value parameters: x: Int)""".stripMargin
    )

  def testDontCallConstructorTwice_1(): Unit =
    assertMessagesText(
      """trait Foo(x: Int)
        |class F extends Foo(1)
        |class Baz extends F with Foo
        |""".stripMargin,
      """Error(Foo(2),Trait Foo is already implemented by superclass F,its constructor cannot be called again)"""
    )

  def testIndirectImplementation(): Unit =
    assertMessagesText(
      """trait Greeting(val name: String)
        |trait FormalGreeting extends Greeting
        |class E extends FormalGreeting
        |""".stripMargin,
      """Error(E,Parameterized trait Greeting is indirectly implemented,needs to be implemented directly so that arguments can be passed)
        |""".stripMargin
    )

  def testSCL21122(): Unit =
    assertNoErrors(
      """object TraitWithDefaultParams {
        |  trait IFoo(x: String = "foo") {
        |    val name = x
        |  }
        |
        |  class Foo extends IFoo // compile passed, but idea reports error
        |}
        |""".stripMargin
    )
}