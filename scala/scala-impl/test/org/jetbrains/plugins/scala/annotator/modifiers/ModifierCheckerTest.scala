package org.jetbrains.plugins.scala
package annotator.modifiers

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ModifierCheckerTest extends SimpleTestCase {
  def testInnerObject(): Unit = {
    assertMatches(messages("object A { final object B }")) {
      case Nil => // SCL-10420
    }
  }

  def testFinalValConstant(): Unit = {
    assertMatches(messages(
      """
        |final class Foo {
        |  final val constant = "This is a constant string that will be inlined"
        |}
      """.stripMargin)) {
      case Nil => // SCL-11500
    }
  }

  def testFinalValConstantAnnotated(): Unit = {
    assertMatches(messages(
      """
        |final class Foo {
        |  final val constant: String = "With annotation there is no inlining"
        |}
      """.stripMargin)) {
      case List(Warning(_,RedundantFinal())) => // SCL-11500
    }
  }

  def testAccessModifierInClass(): Unit = {
    assertNothing(messages(
      """
        |private class Test {
        |  private class InnerTest
        |  private def test(): Unit = ()
        |}
      """.stripMargin
    ))
  }

  def testAccessModifierInBlock(): Unit = {
    assertMessagesSorted(messages(
      """
        |{
        |  private class Test
        |
        |  try {
        |    protected class Test2
        |  }
        |}
      """.stripMargin
    ))(
      Error("private", "'private' modifier is not allowed here"),
      Error("protected", "'protected' modifier is not allowed here")
    )
  }

  // SCL-15981
  def testAbstractMethodInTrait(): Unit = {
    assertMessagesSorted(messages(
      """
        |trait Test {
        |  abstract def foo(): Unit
        |  abstract override def foo2(): Unit
        |}
      """.stripMargin
    ))(
      Error("abstract", "'abstract' modifier allowed only for classes or for definitions with 'override' modifier"),
    )
  }

  def testAbstractMethodInClass(): Unit = {
    assertMessagesSorted(messages(
      """
        |class Test {
        |  abstract def foo(): Unit
        |  abstract override def foo2(): Unit
        |}
      """.stripMargin
    ))(
      Error("abstract", "'abstract' modifier allowed only for classes or for definitions with 'override' modifier"),
      Error("abstract", "'abstract override' modifier only allowed for members of traits"),
    )

    assertMessagesSorted(messages(
      """
        |abstract class Test {
        |  abstract def foo(): Unit
        |  abstract override def foo2(): Unit
        |}
      """.stripMargin
    ))(
      Error("abstract", "'abstract' modifier allowed only for classes or for definitions with 'override' modifier"),
      Error("abstract", "'abstract override' modifier only allowed for members of traits"),
    )
  }

  def testAbstractTrait(): Unit = {
    assertMessagesSorted(messages(
      """
        |abstract trait Test
      """.stripMargin
    ))(
      Warning("abstract", "'abstract' modifier is redundant for traits"),
    )
  }



  private def messages(@Language(value = "Scala") code: String) = {
    val file = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    file.depthFirst().foreach {
      case modifierList: ScModifierList => ModifierChecker.checkModifiers(modifierList)
      case _ =>
    }
    mock.annotations
  }

  val RedundantFinal = StartWith("'final' modifier is redundant")

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }
}

