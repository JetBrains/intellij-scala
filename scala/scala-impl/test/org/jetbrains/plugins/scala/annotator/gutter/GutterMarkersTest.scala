package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => caret}
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.Assert._
import org.junit.Test
import org.junit.experimental.categories.Category

// TODO: split by functionality
@Category(Array(classOf[TypecheckerTests]))
class GutterMarkersTest extends ScalaFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  // TODO Accept a predicate
  protected def testLineMarkerTooltip(expectedTooltipParts: String*): Unit = {
    myFixture.doHighlighting()
    val processed = CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor, getProject, mark => {
      val actualTooltip = mark.getTooltipText
      expectedTooltipParts
        .find(!actualTooltip.contains(_))
        .foreach { missing =>
          assertEquals("Must include", missing, actualTooltip)
        }
      false
    })
    if (processed)
      fail("Gutter mark expected.")
  }

  protected def doTestNoLineMarkers(fileText: String): Unit = {
    doTest(fileText) {
      myFixture.doHighlighting()
      CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor, getProject, _ => {
        fail("No gutters expected.").asInstanceOf[Nothing]
      })
    }
  }

  protected def refToElement(superClass: String, superMethod: String, refText: String): String =
    s"""<a href="#element/$superClass#$superMethod"><code>$refText</code></a>"""

  protected def refToClass(className: String): String =
    s"""<a href="#element/$className"><code>$className</code></a>"""

  protected def recursionTooltip(methodName: String, isTailRecursive: Boolean) =
    s"Method '$methodName' is ${if (isTailRecursive) "tail recursive" else "recursive"}"

  protected def doTest(fileText: String)(testFn: => Any): Unit = {
    val name = getTestName(false)
    myFixture.configureByText(s"$name.scala", StringUtil.convertLineSeparators(fileText, "\n"))
    testFn
  }

  protected def doTestTooltip(fileText: String, tooltipParts: String*): Unit = {
    assertTrue("Tooltip text expected", tooltipParts.nonEmpty)

    doTest(fileText)(testLineMarkerTooltip(tooltipParts: _*))
  }

  @Test
  def testImplements(): Unit = doTestTooltip(
    s"""
       |trait Foo { def x: String }
       |class Bar extends Foo {
       |  override val x: String = "42"$caret
       |}
       """.stripMargin,

    "Implements value from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testOverrides(): Unit = doTestTooltip(
    s"""
       |trait Foo { def x: Int = 42 }
       |trait Bar extends Foo {
       |  override def x: Int = 43$caret
       |}
     """.stripMargin,

    "Overrides method from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testOverridesManyMethods(): Unit = doTestTooltip(
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

    "Overrides methods from 7 classes"
  )

  @Test
  def testRecursionSimple(): Unit = doTestTooltip(
    s"""
       |object A {
       |  def b: Int = b + b$caret
       |}
      """.stripMargin,

    recursionTooltip("b", isTailRecursive = false)
  )

  @Test
  def testTailRecursion(): Unit = doTestTooltip(
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
  def testOverridenTypeMember(): Unit = doTestTooltip(
    s"""
       |trait Foo { type T <: Any }
       |trait Bar extends Foo { override type T <: AnyVal }$caret
   """.stripMargin,

    "Overrides type", refToElement("Foo", "T", "T in Foo")
  )

  @Test
  def testOverridingClassParameter(): Unit = doTestTooltip(
    s"""
       |abstract class Foo { def x: Double }
       |class Bar(override val x: Double) extends Foo$caret
    """.stripMargin,

    "Implements value from", refToElement("Foo", "x", refText = "Foo")
  )

  @Test
  def testSubclassed(): Unit = doTestTooltip(
    s"""
       |class Foo$caret
       |trait Bar extends Foo
     """.stripMargin,

    "Is extended by", refToClass("Bar")
  )

  @Test
  def testSeveralSubclasses(): Unit = doTestTooltip(
    s"""
       |class Foo$caret
       |class Bar extends Foo
       |class Baz extends Bar
     """.stripMargin,

    "Is extended by", refToClass("Bar"), refToClass("Baz")
  )

  @Test
  def testManySubclasses(): Unit = doTestTooltip(
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
  def testManyTraitImplementations(): Unit = doTestTooltip(
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
  def testTraitImplemented(): Unit = doTestTooltip(
    s"""
       |trait Foo$caret
       |trait Bar extends Runnable with Foo
     """.stripMargin,

    "Is mixed into", refToClass("Bar")
  )

  @Test
  def testMemberHasImplementations(): Unit = doTestTooltip(
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
  def testMemberHasOverrides(): Unit = doTestTooltip(
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
  def testLambdaNonTrivial(): Unit = doTestTooltip(
    s"""
       |trait SAM { def f(x: Int, y: Int): Int }
       |object SAM { val f: SAM = _ + _ }$caret
       |
     """.stripMargin,

    "Implements method", refToElement("SAM", "f", "f in SAM")
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
  def testMergedOverridingMarks(): Unit = doTestTooltip(
    s"""
       |trait Foo { def foo: Int; def bar: Int }
       |case class Bar(foo: Int, bar: Int) extends Foo$caret
      """.stripMargin,

    "Multiple overriding members"
  )

  @Test
  def testSCL14208(): Unit = doTestTooltip(
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

  def testTypeAliasOverridesNothing(): Unit = doTestNoLineMarkers(
    s"""trait T {
       |  type S = String$caret
       |}
       |""".stripMargin
  )

  def testTypeAliasOverridesNothing_1(): Unit = doTestNoLineMarkers(
      s"""trait Base {
         |  type P = Int
         |}
         |
         |trait T extends Base {
         |  type S = String$caret
         |}
         |""".stripMargin
    )

  def testTypeAliasOverrides(): Unit = doTestTooltip(
    s"""trait Base {
       |  type MyInt <: Int
       |}
       |
       |trait T extends Base {
       |  override type MyInt = Int$caret
       |}
       |""".stripMargin,

    "Overrides type", refToElement("Base", "MyInt", "MyInt in Base")
  )
}