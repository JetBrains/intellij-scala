package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

abstract class AbsentTypeArgumentAnnotatorTestBase extends AnnotatorSimpleTestCase {
  import Message._

  private final val Prefix =
    """
       object Test {
         class A0
         class A1[X]
         trait A2[X, Y]
    """

  private final val Suffix = "\n}"

  protected def scalaVersion: ScalaVersion

  protected def messagesInContext(@Language(value = "Scala", prefix = Prefix, suffix  = Suffix) code: String): List[Message] =
    messages(s"$Prefix$code$Suffix")

  protected def messages(@Language(value = "Scala") code: String): List[Message] = {
    val file: ScalaFile = parseScalaFile(code, scalaVersion)

    val annotator = new ScalaAnnotator()
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotator.annotate)
    mock.annotations.filter(_.isInstanceOf[Error])
  }
}

class AbsentTypeArgumentAnnotatorTest_Scala2 extends AbsentTypeArgumentAnnotatorTestBase {
  import Message._

  override protected def scalaVersion: ScalaVersion = ScalaVersion.default

  def testSimple(): Unit = {
    assertMatches(messagesInContext("val x: A1 = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A2 = null")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A1[A0] = null")){
      case Nil =>
    }

    assertMatches(messagesInContext("val x: A1[_] = null")){
      case Nil =>
    }
  }

  def testConstructor(): Unit = {
    assertMatches(messagesInContext("val x = new A1()")){
      case Nil =>
    }

    //trait
    assertMatches(messagesInContext("val x = new A2() {}")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x = new A1[A0]()")){
      case Nil =>
    }

    val message = ScalaBundle.message("illegal.instantiation", "Trait", "A2")
    assertMatches(messagesInContext("val x = new A2[A0, A0]()")){
      case List(Error("A2[A0, A0]", `message`)) =>
    }

    assertMatches(messagesInContext("val x = new A2[A0, A0]() {}")){
      case Nil =>
    }

    assertMatches(messagesInContext("val x = new A1[A1]()")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }
  }

  def testInTypeArg(): Unit = {
    assertMatches(messagesInContext("val x: A1[A1] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A1[A1] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A1[A2] = null")){
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A2[A1, A0] = null")){
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: A2[A0, A1[_]] = null")){
      case Nil =>
    }
  }

  def testPattern(): Unit = {
    assertMatches(messagesInContext("val x: Any = null; x match { case _: A1 => }")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: Any = null; x match { case _: A2 => }")) {
      case List(Error(_, "Type A2 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: Any = null; x match { case _: A1[A1] => }")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("val x: Any = null; x match { case _: A1[_] => }")) {
      case Nil =>
    }

    assertMatches(messagesInContext("val x: Any = null; x match { case _: A1[A0] => }")) {
      case Nil =>
    }
  }

  def testInfixType(): Unit = {
    assertMatches(messagesInContext("type T = A1 A2 A0")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("type T = A0 A2 A1")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("class C[H[_], Z]; type T = A1 C A0")) {
      case Nil =>
    }
  }

  def testParentheses(): Unit = {
    assertMatches(messagesInContext("type T = (A1)[Int]")) {
      case Nil =>
    }

    assertMatches(messagesInContext("type T = ((A1))[Int]")) {
      case Nil =>
    }

    assertMatches(messagesInContext("type T = (A1)")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }

    assertMatches(messagesInContext("type T = ((A1))")) {
      case List(Error(_, "Type A1 takes type parameters")) =>
    }
  }
}

class AbsentTypeArgumentAnnotatorTest_Scala3 extends AbsentTypeArgumentAnnotatorTest_Scala2 {

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3

  def testParameterlessFunctionWithStableReturnType(): Unit =
    assertNothing(messages(
      """object Wrapper2 {
        |  def f1[T]: "literal" = "literal"
        |
        |  val b1: f1.type = "literal"
        |}
        |""".stripMargin
    ))
}
