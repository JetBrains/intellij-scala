package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * @author Nikolay.Tropin
  */
class AbsentTypeArgumentAnnotatorTest extends SimpleTestCase {
  private val prefix =
    """object Test {
      |  class A0
      |  class A1[X]
      |  trait A2[X, Y]
      |
      |""".stripMargin
  private val postfix = "\n}"

  def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = s"$prefix$code$postfix".parse

    val annotator = ScalaAnnotator.forProject
    val mock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate(_, mock))
    mock.annotations.filter(_.isInstanceOf[Error])
  }


  def testSimple(): Unit = {
    assertMatches(messages("val x: A1 = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: A2 = null")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messages("val x: A1[A0] = null")){
      case Nil =>
    }

    assertMatches(messages("val x: A1[_] = null")){
      case Nil =>
    }
  }

  def testConstructor(): Unit = {
    assertMatches(messages("val x = new A1()")){
      case Nil =>
    }

    //trait
    assertMatches(messages("val x = new A2() {}")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messages("val x = new A1[A0]()")){
      case Nil =>
    }

    assertMatches(messages("val x = new A2[A0, A0]()")){
      case List(Error("A2[A0, A0]", "Trait A2 is abstract; cannot be instantiated")) =>
    }

    assertMatches(messages("val x = new A2[A0, A0]() {}")){
      case Nil =>
    }

    assertMatches(messages("val x = new A1[A1]()")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }
  }

  def testInTypeArg(): Unit = {
    assertMatches(messages("val x: A1[A1] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: A1[A1] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: A1[A2] = null")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messages("val x: A2[A1, A0] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: A2[A0, A1[_]] = null")){
      case Nil =>
    }
  }

  def testPattern(): Unit = {
    assertMatches(messages("val x: Any = null; x match { case _: A1 => }")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: Any = null; x match { case _: A2 => }")) {
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messages("val x: Any = null; x match { case _: A1[A1] => }")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("val x: Any = null; x match { case _: A1[_] => }")) {
      case Nil =>
    }

    assertMatches(messages("val x: Any = null; x match { case _: A1[A0] => }")) {
      case Nil =>
    }
  }

  def testInfixType(): Unit = {
    assertMatches(messages("type T = A1 A2 A0")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("type T = A0 A2 A1")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messages("class C[H[_], Z]; type T = A1 C A0")) {
      case Nil =>
    }
  }
}
