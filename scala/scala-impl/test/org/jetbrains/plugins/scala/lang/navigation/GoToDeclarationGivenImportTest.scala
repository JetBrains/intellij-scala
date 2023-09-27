package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGiven, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScReferencePatternImpl
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class GoToDeclarationGivenImportTest extends GotoDeclarationTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def doTest(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    configureFromFileText(fileText)

    val editor = getEditor
    val targets = GotoDeclarationAction.findAllTargetElements(getProject, editor, editor.getCaretModel.getOffset).toSeq

    checkTargets(targets, expected)
  }

  private val ExpectedCaret = "<expected-caret>"
  private val ActionId = "GotoDeclaration"

  private def checkNavigation(fileText: String): Unit = {
    configureFromFileText(fileText.replace(ExpectedCaret, ""))
    myFixture.performEditorAction(ActionId)

    val expectedText = fileText.replace(CARET, "").replace(ExpectedCaret, CARET)
    myFixture.checkResult(expectedText.trim, true)
  }

  def testGoToWildcardGiven(): Unit = doTest(
    s"""object GoToGivenImport {
       |  import Givens.${CARET}given
       |}
       |
       |object Givens:
       |  given str: String = "foo"
       |  given Int = 42
       |  implicit val short: Short = ???
       |  implicit var float: Float = ???
       |  implicit def long: Long = ???
       |  implicit object MyObject extends MyTrait
       |
       |trait MyTrait
       |
       |""".stripMargin,
    expected = (is[ScGiven], "str"),
    (is[ScGiven], "given_Int"),
    (is[ScReferencePatternImpl], "short"),
    (is[ScReferencePatternImpl], "float"),
    (is[ScFunctionDefinition], "long"),
    (is[ScObject], "Givens.MyObject"),
  )

  def testGoToWildcardGivenFromInstance(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar(val i: Int) {
       |    given number: Int = i * 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.Bar
       |
       |  val bar = Bar(21)
       |
       |  import bar.giv${CARET}en
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "number")
  )

  def testGoToGivenSelectorByType_caretOnKeyword(): Unit = doTest(
    s"""
       |object Foo {
       |  given str: String = "foo"
       |  given Int = 42
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en Int
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_Int")
  )

  def testGoToGivenSelectorByType_caretOnType(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given Bar = Bar()
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given B${CARET}ar
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_Bar")
  )

  def testGoToGivenSelectorByType_caretOnTypeParameter(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given List[Bar] = Bar() :: Nil
       |  given List[String] = "foo" :: Nil
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given List[Ba${CARET}r]
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_List_Bar")
  )

  def testGoToGivenSelectorByType_caretOnParameterizedType(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given List[Bar] = Bar() :: Nil
       |  given List[String] = "foo" :: Nil
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given Li${CARET}st[Bar]
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_List_Bar")
  )

  def testGoToGivenSelectorByType_compoundDefinition(): Unit = doTest(
    s"""
       |object Foo {
       |  trait Bar
       |  trait Baz
       |  given barBaz: Bar with Baz with {
       |    val x = 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.*
       |  import Foo.given Ba${CARET}r
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "Foo.barBaz")
  )

  def testGoToGivenSelectorByType_compoundDefinition2(): Unit = doTest(
    s"""
       |object Foo {
       |  trait Bar
       |  trait Baz
       |  given barBaz: Bar with Baz with {
       |    val x = 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.*
       |  import Foo.given Ba${CARET}z
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "Foo.barBaz")
  )

  def testGoToImportedGivenName(): Unit = doTest(
    s"""
       |object Foo {
       |  given str: String = "foo"
       |  given Int = 42
       |}
       |
       |object Test {
       |  import Foo.s${CARET}tr
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "str")
  )

  def testGoToImportedGivenNameFromInstance(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar(val i: Int) {
       |    given number: Int = i * 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.Bar
       |
       |  val bar = Bar(21)
       |
       |  import bar.num${CARET}ber
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "number")
  )

  def testGoToGivenSelectorByType_caretAtTheEndOfLine(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar
       |  given Bar = Bar()
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given Bar$CARET
       |}
       |""".stripMargin,
    expected = (is[ScGiven], "given_Bar")
  )

  def testNavigation_namedGivenAlias(): Unit = checkNavigation(
    s"""
       |object Foo {
       |  given ${ExpectedCaret}number: Int = 2
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en
       |}
       |""".stripMargin
  )

  def testNavigation_anonymousGivenAlias(): Unit = checkNavigation(
    s"""
       |object Foo {
       |  given ${ExpectedCaret}Int = 2
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en
       |}
       |""".stripMargin
  )

  def testNavigation_namedGivenDefinition(): Unit = checkNavigation(
    s"""
       |object Foo {
       |  class Bar
       |  given ${ExpectedCaret}bar: Bar with {
       |    val x = 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en
       |}
       |""".stripMargin
  )

  def testNavigation_anonymousGivenDefinition(): Unit = checkNavigation(
    s"""
       |object Foo {
       |  class Bar
       |  given ${ExpectedCaret}Bar with {
       |    val x = 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.giv${CARET}en
       |}
       |""".stripMargin
  )

  def testNavigation_compoundAnonymousGivenDefinition(): Unit = checkNavigation(
    s"""
       |object Foo {
       |  trait Bar
       |  trait Baz
       |  given ${ExpectedCaret}Bar with Baz with {
       |    val x = 2
       |  }
       |}
       |
       |object Test {
       |  import Foo.Bar
       |  import Foo.given Ba${CARET}r
       |}
       |""".stripMargin
  )

  def testGoToWildcardGiven_WithImplicits(): Unit = doTest(
    s"""
       |object GoToGivenImport {
       |  import Givens.giv${CARET}en
       |}
       |
       |object Givens:
       |  given string: String = "42"
       |  given Int = 42
       |  given Appendable with {
       |    override def append(csq: CharSequence): Appendable = ???
       |    override def append(csq: CharSequence, start: Int, end: Int): Appendable = ???
       |    override def append(c: Char): Appendable = ???
       |  }
       |  given short(using Int): Short = 42
       |
       |  val regularVal: Int = 1
       |  def regularDef: Boolean = false
       |
       |  implicit val boolean: Boolean = true
       |  implicit val (list, vector) = (List(1, 2, 3), Vector(true, false))
       |  implicit def double: Double = 1.2
       |  implicit def long(using i: Int): Long = i + 2L
       |
       |  implicit class StringExt(private val str: String) extends AnyVal {
       |    def scream: String = str.toUpperCase
       |  }
       |
       |  class RegularClass(private val str: String) extends AnyVal {
       |    def whisper: String = str.toLowerCase
       |  }
       |""".stripMargin,
    expected =
      (_.is[ScGiven], "string"),
      (_.is[ScGiven], "given_Int"),
      (_.is[ScGiven], "Givens.given_Appendable"),
      (_.is[ScGiven], "short"),
      (_.is[ScReferencePattern], "boolean"),
      (_.is[ScReferencePattern], "list"),
      (_.is[ScReferencePattern], "vector"),
      (_.is[ScFunctionDefinition], "double"),
      (_.is[ScFunctionDefinition], "long"),
      (_.is[ScClass], "Givens.StringExt")
  )

  def testGoToWildcardGivenFromInstance_WithImplicits(): Unit = doTest(
    s"""
       |object Foo {
       |  class Bar(val i: Int) {
       |    given string: String = "42"
       |    given Int = 42
       |    given Appendable with {
       |      override def append(csq: CharSequence): Appendable = ???
       |      override def append(csq: CharSequence, start: Int, end: Int): Appendable = ???
       |      override def append(c: Char): Appendable = ???
       |    }
       |    given short(using Int): Short = 42
       |
       |    val regularVal: Int = 1
       |    def regularDef: Boolean = false
       |
       |    implicit val boolean: Boolean = true
       |    implicit val (list, vector) = (List(1, 2, 3), Vector(true, false))
       |    implicit def double: Double = 1.2
       |    implicit def long(using i: Int): Long = i + 2L
       |
       |    implicit class StringExt(private val str: String) extends AnyVal {
       |      def scream: String = str.toUpperCase
       |    }
       |
       |    class RegularClass(private val str: String) extends AnyVal {
       |      def whisper: String = str.toLowerCase
       |    }
       |  }
       |}
       |
       |object Test {
       |  import Foo.Bar
       |
       |  val bar = Bar(21)
       |
       |  import bar.giv${CARET}en
       |}
       |""".stripMargin,
    expected =
      (_.is[ScGiven], "string"),
      (_.is[ScGiven], "given_Int"),
      (_.is[ScGiven], "Foo.Bar.given_Appendable"),
      (_.is[ScGiven], "short"),
      (_.is[ScReferencePattern], "boolean"),
      (_.is[ScReferencePattern], "list"),
      (_.is[ScReferencePattern], "vector"),
      (_.is[ScFunctionDefinition], "double"),
      (_.is[ScFunctionDefinition], "long"),
      (_.is[ScClass], "Foo.Bar.StringExt")
  )

  def testGoToGivenSelectorByType_WithImplicits(): Unit = doTest(
    s"""
       |object Foo {
       |  given string: String = "42"
       |  given Int = 42
       |  given Appendable with {
       |    override def append(csq: CharSequence): Appendable = ???
       |    override def append(csq: CharSequence, start: Int, end: Int): Appendable = ???
       |    override def append(c: Char): Appendable = ???
       |  }
       |  given short(using Int): Short = 42
       |
       |  val regularVal: Int = 1
       |  def regularDef: Boolean = false
       |
       |  implicit val boolean: Boolean = true
       |  implicit val (list, vector) = (List(1, 2, 3), Vector(true, false))
       |  implicit def double: Double = 1.2
       |  implicit def long(using i: Int): Long = i + 2L
       |
       |  implicit class StringExt(private val str: String) extends AnyVal {
       |    def scream: String = str.toUpperCase
       |  }
       |
       |  class RegularClass(private val str: String) extends AnyVal {
       |    def whisper: String = str.toLowerCase
       |  }
       |}
       |
       |object Test {
       |  type T = String | Int | Boolean | Double
       |  import Foo.giv${CARET}en T
       |}
       |""".stripMargin,
    expected =
      (_.is[ScGiven], "string"),
      (_.is[ScGiven], "given_Int"),
      (_.is[ScReferencePattern], "boolean"),
      (_.is[ScFunctionDefinition], "double"),
  )
}
