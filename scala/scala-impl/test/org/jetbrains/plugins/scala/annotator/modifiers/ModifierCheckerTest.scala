package org.jetbrains.plugins.scala.annotator.modifiers

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage, TypecheckerTests}
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class ModifierCheckerTestBase extends SimpleTestCase {
  protected def scalaLanguage: com.intellij.lang.Language

  protected def messages(@Language(value = "Scala") code: String) = {
    val file = parseText(code, scalaLanguage)

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

@Category(Array(classOf[TypecheckerTests]))
class ModifierCheckerTest_Scala_2 extends ModifierCheckerTestBase {
  override protected def scalaLanguage: com.intellij.lang.Language = ScalaLanguage.INSTANCE

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

  protected val LazyValueCode =
    """abstract class A {
      |  lazy val value: String
      |}
      |""".stripMargin
  def testLazyValue(): Unit = {
    assertMessagesSorted(messages(LazyValueCode))(
      Error("lazy", "lazy values may not be abstract")
    )
  }

  protected val LazyVariableCode =
    """abstract class A {
      |  lazy var variable: String
      |}
      |""".stripMargin
  def testLazyVariable(): Unit = {
    assertMessagesSorted(messages(LazyVariableCode))(
      Error("lazy", "'lazy' modifier allowed only with value definitions")
    )
  }
}

@Category(Array(classOf[TypecheckerTests]))
class ModifierCheckerTest_Scala_3 extends ModifierCheckerTest_Scala_2 {
  override protected def scalaLanguage = Scala3Language.INSTANCE

  override protected def messages(@Language(value = "Scala 3") code: String) =
    super.messages(code)

  override def testLazyValue(): Unit =
    assertNothing(messages(LazyValueCode))

  def testFinalInTopLevelDefinitionsWithAssignment(): Unit = {
    assertNothing(messages(
      """final val value = ???
        |final lazy val lazyVal = ???
        |final var variable = ???
        |final def foo = ???
        |final given x: Int = ???
        |final type alias = String
        |""".stripMargin))
  }
}