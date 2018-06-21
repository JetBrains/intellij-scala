package org.jetbrains.plugins.scala
package annotator.modifiers

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

class ModifierCheckerTest extends SimpleTestCase {
  def testToplevelObject(): Unit = {
    assertMatches(messages("final object A")) {
      case List(Warning(_,RedundantFinal())) =>
    }
  }

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

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file = code.parse
    val modifiers = file.depthFirst().filterByType[ScModifierList]

    val mock = new AnnotatorHolderMock(file)

    modifiers.foreach(ModifierChecker.checkModifiers(_,mock))
    mock.annotations
  }

  val RedundantFinal = StartWith("'final' modifier is redundant")

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }
}

