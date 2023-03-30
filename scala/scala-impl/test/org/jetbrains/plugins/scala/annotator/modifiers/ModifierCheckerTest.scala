package org.jetbrains.plugins.scala.annotator.modifiers

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.{Scala3Language, ScalaBundle, ScalaLanguage, TypecheckerTests}
import org.junit.experimental.categories.Category

/**
 * See also in [[org.jetbrains.plugins.scala.annotator.modifiers.GeneratedModifierTest]]
 */
@Category(Array(classOf[TypecheckerTests]))
abstract class ModifierCheckerTestBase extends SimpleTestCase {
  import Message._

  protected def scalaLanguage: com.intellij.lang.Language

  protected def messages(code: String) = {
    val file = parseText(code, scalaLanguage)

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    file.depthFirst().foreach {
      case modifierList: ScModifierList => ModifierChecker.checkModifiers(modifierList)
      case _ =>
    }
    mock.annotations
  }

  protected val RedundantFinal = StartWith("'final' modifier is redundant")
  protected val ImplicitClassMustHaveSingleConstructorParameter =
    "implicit class must have a primary constructor with exactly one argument in first parameter list"

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }

  protected def illegalCombinationPairedErrors(modifier1: String, modifier2: String): Seq[Error] = Seq(
    Error(modifier1, ScalaBundle.message("illegal.modifiers.combination", modifier1, modifier2)),
    Error(modifier2, ScalaBundle.message("illegal.modifiers.combination", modifier2, modifier1))
  )

  def testIllegalCombination_FinalSealed(): Unit = {
    assertMessages(messages("""final sealed class A1"""))(
      illegalCombinationPairedErrors("final", "sealed"): _*
    )
  }

  def testIllegalCombination_PrivateProtected(): Unit = {
    assertMessages(messages("""class Wrapper { private protected class A1 }"""))(
      illegalCombinationPairedErrors("private", "protected"): _*
    )
  }

  def testValidImplicitClass1(): Unit = {
    assertNothing(messages("""object Wrapper { implicit class A(x: Int) }"""))
  }

  def testValidImplicitClass2(): Unit = {
    assertNothing(messages("""object Wrapper { implicit class A(x: Int)(implicit y: String) }"""))
  }

  def testValidImplicitClass3(): Unit = {
    assertNothing(messages("""object Wrapper { implicit class A(x: Int)(y: String) }"""))
  }

  def testValidImplicitClass4(): Unit = {
    assertNothing(messages("""object Wrapper { implicit class A(x: Int)(y: String)(x: Int) }"""))
  }

  def testInvalidImplicitClass1(): Unit = {
    assertMessages(messages("""object Wrapper { implicit class A }"""))(
      Error("implicit", ImplicitClassMustHaveSingleConstructorParameter),
    )
  }

  def testInvalidImplicitClass2(): Unit = {
    assertMessages(messages("""object Wrapper { implicit class A() }"""))(
      Error("implicit", ImplicitClassMustHaveSingleConstructorParameter),
    )
  }

  def testInvalidImplicitClass3(): Unit = {
    assertMessages(messages("""object Wrapper { implicit class A(x: Int, y: Int) }"""))(
      Error("implicit", ImplicitClassMustHaveSingleConstructorParameter),
    )
  }

  def testDuplicateModifierPrivate(): Unit = {
    assertMessages(messages(
      """private private object A
        |""".stripMargin)
    )(
      Error("private", ScalaBundle.message("illegal.modifiers.combination", "private", "private")),
    )
  }

  def testDuplicateModifierFinal(): Unit = {
    assertMessages(messages(
      """final final object A
        |""".stripMargin)
    )(
      Error("final", ScalaBundle.message("illegal.modifiers.combination", "final", "final")),
    )
  }
}

@Category(Array(classOf[TypecheckerTests]))
class ModifierCheckerTest_Scala_2 extends ModifierCheckerTestBase {
  import Message._

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
  import Message._

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

  protected val RedundantOpen = "'open' modifier is redundant for this definition"
  protected val OnlyClassesCanBeOpen = "Only classes can be open"
  protected val IllegalOpaqueModifier = "'opaque' modifier allowed only for type aliases"
  protected val RepeatedModifier = "repeated modifier"

  def testOpenModifierIsRedundant(): Unit = {
    assertMessages(messages(
      """open abstract class A1
        |open trait A2
        |open object A3
        |""".stripMargin)
    )(
      Warning("open", RedundantOpen),
      Warning("open", RedundantOpen),
      Warning("open", RedundantOpen),
    )
  }

  def testOpenIsAllowedInClass(): Unit = {
    assertNothing(messages(
      """open class A
        |""".stripMargin
    ))
  }

  def testOpenIsNotAllowedInNonClasses(): Unit = {
    assertMessages(messages(
      """open def foo1 = 42
        |open type foo2 = 42
        |""".stripMargin
    ))(
      Error("open", OnlyClassesCanBeOpen),
      Error("open", OnlyClassesCanBeOpen),
    )
  }

  def testIllegalCombination_FinalOpen(): Unit = {
    assertMessages(messages("""final open class A1""".stripMargin))(
      illegalCombinationPairedErrors("final", "open"): _*
    )
  }

  def testIllegalCombination_OpenFinal(): Unit = {
    assertMessages(messages("""open final class A1""".stripMargin))(
      illegalCombinationPairedErrors("open", "final"): _*
    )
  }

  def testIllegalCombination_SealedOpen(): Unit = {
    assertMessages(messages("""sealed open class A1""".stripMargin))(
      illegalCombinationPairedErrors("sealed", "open"): _*
    )
  }

  def testIllegalCombination_LazyInline(): Unit = {
    assertMessages(messages("""lazy inline val A = 42"""))(
      illegalCombinationPairedErrors("lazy", "inline"): _*
    )
  }

  def testIllegalCombination_InlineLazy(): Unit = {
    assertMessages(messages("""inline lazy val A = 42"""))(
      illegalCombinationPairedErrors("inline", "lazy"): _*
    )
  }

  def testValidImplicitClassTopLevel(): Unit = {
      assertNothing(messages("""implicit class A(x: Int)"""))
  }

  def testLegalOpaqueModifier(): Unit = {
    assertNothing(messages(
      """object Wrapper {
        |  opaque type MyType = Int
        |}
        |""".stripMargin
    ))
  }

  def testIllegalOpaqueModifier(): Unit = {
    assertMessages(messages(
      """object Wrapper {
        |  opaque class MyClass
        |  opaque val x = 42
        |}
        |""".stripMargin)
    )(
      Error("opaque", IllegalOpaqueModifier),
      Error("opaque", IllegalOpaqueModifier),
    )
  }
}