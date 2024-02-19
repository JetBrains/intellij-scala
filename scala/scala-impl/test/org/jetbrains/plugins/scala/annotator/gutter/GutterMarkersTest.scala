package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => caret}
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.Assert._
import org.junit.Test
import org.junit.experimental.categories.Category

// TODO: split by functionality
@Category(Array(classOf[TypecheckerTests]))
class GutterMarkersTest extends GutterMarkersTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  private def refToTypeMember(cls: String, tpe: String): String =
    refToMember(cls, tpe, applyStyleForMember = false)

  @Test
  def testImplements(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo { def x: String }
       |class Bar extends Foo {
       |  override val x: String = "42"$caret
       |}
       """.stripMargin,

    "Implements member from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testOverrides(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo { def x: Int = 42 }
       |trait Bar extends Foo {
       |  override def x: Int = 43$caret
       |}
     """.stripMargin,

    "Overrides member from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testOverridesManyMethods(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo { def x: Int = 42 }
       |trait Foo1 extends Foo  { override def x: Int = 43}
       |trait Foo2 extends Foo1 { override def x: Int = 43}
       |trait Foo3 extends Foo2 { override def x: Int = 43}
       |trait Foo4 extends Foo3 { override def x: Int = 43}
       |trait Foo5 extends Foo4 { override def x: Int = 43}
       |trait Foo6 extends Foo5 { override def x: Int = 43}
       |
       |trait Bar extends Foo6 {
       |  override def x: Int = 43$caret
       |}
     """.stripMargin,

    "Overrides members from 7 classes"
  )

  @Test
  def testRecursionSimple(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |object A {
       |  def b: Int =$caret
       |    b + b
       |}
      """.stripMargin,

    recursionTooltip("b", isTailRecursive = false)
  )

  def testRecursionSimpleSameLineAsDefinition(): Unit = doTestAllTooltipsAtCaret(
    s"""
       |object A {
       |  def b: Int = b + b$caret
       |}
      """.stripMargin,

    recursionTooltip("b", isTailRecursive = false),
    recursiveCallTooltip
  )

  @Test
  def testTailRecursion(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |object B {
       |  def filter(p: Int => Boolean, xs: List[Int]): List[Int] = {
       |    def loop(ys: List[Int], acc: List[Int] = Nil): List[Int] = ys match {$caret
       |      case y :: rest => if (p(y)) loop(rest, y :: acc) else loop(rest, acc)
       |      case Nil => acc
       |    }
       |    loop(xs)
       |  }
       |}
    """.stripMargin,

    recursionTooltip("loop", isTailRecursive = true)
  )

  @Test
  def testRecursiveCallOneTooltipPerLine(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |object C {
       |  def fn(a: Int, b: Int, c: Int): Unit = {
       |    if (a > 0) fn(-a, b, c) else if (b > 0) fn(a, -b, c) else if (c > 0) fn(a, b, -c) else fn(-a, -b, -c)$caret
       |
       |    fn(1, 2, 3)
       |  }
       |}
    """.stripMargin,

    // add gutter marker only once
    recursiveCallTooltip
  )

  @Test
  def testRecursiveCallOneTooltipPerLine2(): Unit = doTestAllTooltipsAtCaret(
    s"""
       |object C {
       |  def fn(a: Int, b: Int, c: Int): Unit = {
       |    if (a > 0) fn(-a, b, c) else if (b > 0) fn(a, -b, c) else if (c > 0) fn(a, b, -c) else fn(-a, -b, -c)
       |
       |    fn(1, 2, 3)$caret
       |  }
       |}
    """.stripMargin,

    // add gutter marker only once **per line**
    recursiveCallTooltip
  )

  @Test
  def testOverridenTypeMember(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo { type T <: Any }
       |trait Bar extends Foo { override type T <: AnyVal }$caret
   """.stripMargin,

    "Overrides type", refToTypeMember("Foo", "T")
  )

  @Test
  def testOverridingClassParameter(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |abstract class Foo { def x: Double }
       |class Bar(override val x: Double) extends Foo$caret
    """.stripMargin,

    "Implements member from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testSubclassed(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |class Foo$caret
       |trait Bar extends Foo
     """.stripMargin,

    "Is extended by", refToClass("Bar")
  )

  @Test
  def testSeveralSubclasses(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |class Foo$caret
       |class Bar extends Foo
       |class Baz extends Bar
     """.stripMargin,

    "Is extended by", refToClass("Bar"), refToClass("Baz")
  )

  @Test
  def testManySubclasses(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |class Foo$caret
       |class Bar1 extends Foo
       |class Bar2 extends Foo
       |class Bar3 extends Foo
       |class Bar4 extends Foo
       |class Bar5 extends Foo
       |class Bar6 extends Foo
       |class Bar7 extends Foo
     """.stripMargin,

    "Is extended by 7 subclasses"
  )

  @Test
  def testManyTraitImplementations(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo$caret
       |class Bar1 extends Foo
       |class Bar2 extends Foo
       |class Bar3 extends Foo
       |class Bar4 extends Foo
       |class Bar5 extends Foo
       |class Bar6 extends Foo
       |class Bar7 extends Foo
     """.stripMargin,

    "Is mixed into 7 classes"
  )

  @Test
  def testTraitImplemented(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo$caret
       |trait Bar extends Runnable with Foo
     """.stripMargin,

    "Is mixed into", refToClass("Bar")
  )

  @Test
  def testMemberHasImplementations(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo {
       |  def foo: Int$caret
       |}
       |trait Bar extends Runnable with Foo {
       |  def foo: Int = 42
       |}
     """.stripMargin,

    "Member has implementations"
  )

  @Test
  def testMemberHasOverrides(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo {
       |  def foo: Int = 0$caret
       |}
       |trait Bar extends Runnable with Foo {
       |  def foo: Int = 42
       |}
     """.stripMargin,

    "Member has overrides"
  )

  @Test
  def testLambdaTrivial(): Unit = doTest(
    s"""
       |object Trivial {
       |  List(1, 2, 3).map(x => x + 1)$caret
       |}
     """.stripMargin
  ) {
    myFixture.doHighlighting()
    if (!CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor, getProject, Function.const(false)))
      fail("Gutter mark expected.")
  }

  @Test
  def testMergedOverridingMarks(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo { def foo: Int; def bar: Int }
       |case class Bar(foo: Int, bar: Int) extends Foo$caret
      """.stripMargin,

    "Multiple overriding members"
  )

  @Test
  def testSCL14208(): Unit = doTestSingleTooltipAtCaret(
    s"""
       |trait Foo {
       |  val a: Int$caret
       |}
       |class Bar extends Foo {
       |  override val a: Int = 42
       |}
    """.stripMargin,

    "Member has implementations"
  )

  def testTypeAliasOverridesNothing(): Unit = doTestNoLineMarkersAtCaret(
    s"""trait T {
       |  type S = String$caret
       |}
       |""".stripMargin
  )

  def testTypeAliasOverridesNothing_1(): Unit = doTestNoLineMarkersAtCaret(
      s"""trait Base {
         |  type P = Int
         |}
         |
         |trait T extends Base {
         |  type S = String$caret
         |}
         |""".stripMargin
    )

  def testTypeAliasOverrides(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  type MyInt <: Int
       |}
       |
       |trait T extends Base {
       |  override type MyInt = Int$caret
       |}
       |""".stripMargin,

    "Overrides type", refToTypeMember("Base", "MyInt")
  )

  // SCL-16103
  def testDontShowOverrideGutterForBeanPropertyIfItDoesNotOverrideAnything(): Unit =
    doTestNoLineMarkers(
      """import scala.beans.BeanProperty
        |
        |class A {
        |  @BeanProperty var x: Int = 42
        |}
        |""".stripMargin
    )

  protected val SetterAndGetterTraitsCode =
    """trait Setter {
      |  def setValue(foo: String): Unit
      |}
      |
      |trait Getter {
      |  def getValue: String
      |}
      |
      |trait GetterWithSetter extends Getter with Setter
      |""".stripMargin

  def testShowOverrideGutterForBeanPropertyIfItOverridesSomeMethod_BeanAsField(): Unit = {
    myFixture.addFileToProject("common.scala", SetterAndGetterTraitsCode)
    doTestAllGuttersParts(
      """import scala.beans.BeanProperty
        |
        |class A1 extends Getter { @BeanProperty var value = "foo" }
        |class B1 extends Setter { @BeanProperty var value = "foo" }
        |class C1 extends GetterWithSetter { @BeanProperty var value = "foo" }
        |""".stripMargin,
      Seq(
        ExpectedGutterParts(3, (77, 82), "Implements member from", refToElement("Getter", "getValue", "Getter")),
        ExpectedGutterParts(4, (137, 142), "Implements member from", refToElement("Setter", "setValue", "Setter")),
        ExpectedGutterParts(5, (207, 212), "Implements member from", refToElement("Getter", "getValue", "Getter"), refToElement("Setter", "setValue", "Setter")),
      )
    )
  }

  def testShowOverrideGutterForBeanPropertyIfItOverridesSomeMethod_BeanAsFieldAndConstructorParameter(): Unit = {
    myFixture.addFileToProject("common.scala", SetterAndGetterTraitsCode)
    doTestAllGuttersParts(
      """import scala.beans.BeanProperty
        |
        |class A2(@BeanProperty val value: String) extends Getter
        |class B2(@BeanProperty var value: String) extends Setter
        |class C2(@BeanProperty var value: String) extends GetterWithSetter
        |""".stripMargin,
      Seq(
        ExpectedGutterParts(3, (60, 65), "Implements member from", refToElement("Getter", "getValue", "Getter")),
        ExpectedGutterParts(4, (117, 122), "Implements member from", refToElement("Setter", "setValue", "Setter")),
        ExpectedGutterParts(5, (174, 179), "Implements member from", refToElement("Getter", "getValue", "Getter"), refToElement("Setter", "setValue", "Setter")),
      )
    )
  }
}