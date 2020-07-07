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
  protected def testLineMarker(expectedTooltip: String): Unit = {
    myFixture.doHighlighting()
    val processed = CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor, getProject, mark => {
      val actualTooltip = mark.getTooltipText
      if (!actualTooltip.contains(expectedTooltip)) {
        assertEquals("Must include", expectedTooltip, actualTooltip)
      }
      false
    })
    if (processed)
      fail("Gutter mark expected.")
  }

  protected def doTestNoLineMarkers(): Unit = {
    myFixture.doHighlighting()
    CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor, getProject, _ => {
      fail("No gutters expected.").asInstanceOf[Nothing]
    })
  }

  // TODO Use the strings directly (or we have to test the test or something)
  protected def testOverridesImplementsMarker(superClass: String, superMethod: String, isOverride: Boolean, member: String, presentation: String): Unit =
    testLineMarker(
      s"""${if (isOverride) "Overrides" else "Implements"} $member in <a href="#element/$superClass#$superMethod"><code>$presentation</code></a>"""
    )

  protected def testImplementsMarker(superName: String, superMethod: String, member: String)(presentation: String = superName): Unit =
    testOverridesImplementsMarker(superName, superMethod, isOverride = false, member, presentation)

  protected def testOverridesMarker(superName: String, superMethod: String, member: String)(presentation: String = superName): Unit =
    testOverridesImplementsMarker(superName, superMethod, isOverride = true, member, presentation)

  protected def testRecursionMarker(methodName: String, isTailRecursive: Boolean = false): Unit =
    testLineMarker(s"Method '$methodName' is ${if (isTailRecursive) "tail recursive" else "recursive"}")

  protected def testHasSubclassesMarker(isTrait: Boolean): Unit =
    testLineMarker(s"${if (isTrait) "Is mixed into" else "Class has subclasses"}")

  protected def testIsOverridenMarker(isOverride: Boolean = true): Unit =
    testLineMarker(s"Member has ${if (isOverride) "overrides" else "implementations"}")

  protected def doTest(fileText: String)(testFn: => Any): Unit = {
    val name = getTestName(false)
    myFixture.configureByText(s"$name.scala", StringUtil.convertLineSeparators(fileText, "\n"))
    testFn
  }

  @Test
  def testImplements(): Unit = doTest(
      s"""
         |trait Foo { def x: String }
         |class Bar extends Foo {
         |  override val x: String = "42"$caret
         |}
       """.stripMargin,
  )(testImplementsMarker("Foo", "x", "value")())

  @Test
  def testOverrides(): Unit = doTest(
    s"""
       |trait Foo { def x: Int = 42 }
       |trait Bar extends Foo {
       |  override def x: Int = 43$caret
       |}
     """.stripMargin
  )(testOverridesMarker("Foo", "x", "method")())

  @Test
  def testRecursionSimple(): Unit = doTest(
    s"""
       |object A {
       |  def b: Int = b + b$caret
       |}
     """.stripMargin
  )(testRecursionMarker("b"))

  @Test
  def testTailRecursion(): Unit = doTest(
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
    """.stripMargin
  )(testRecursionMarker("loop", isTailRecursive = true))

  @Test
  def testOverridenTypeMember(): Unit = doTest(
  s"""
     |trait Foo { type T <: Any }
     |trait Bar extends Foo { override type T <: AnyVal }$caret
   """.stripMargin
  )(testOverridesMarker("Foo", "T", "type")("T in Foo"))

  @Test
  def testOverridingClassParameter(): Unit = doTest(
   s"""
      |abstract class Foo { def x: Double }
      |class Bar(override val x: Double) extends Foo$caret
    """.stripMargin
  )(testImplementsMarker("Foo", "x", "value")())

  @Test
  def testSubclassed(): Unit = doTest(
    s"""
       |class Foo$caret
       |trait Bar extends Foo
     """.stripMargin
  )(testHasSubclassesMarker(isTrait = false))

  @Test
  def testTraitImplemented(): Unit = doTest(
    s"""
       |trait Foo$caret
       |trait Bar extends Runnable with Foo
     """.stripMargin
  )(testHasSubclassesMarker(isTrait = true))

  @Test
  def testLambdaNonTrivial(): Unit = doTest(
    s"""
       |trait SAM { def f(x: Int, y: Int): Int }
       |object SAM { val f: SAM = _ + _ }$caret
       |
     """.stripMargin
  )(testLineMarker("Implements method in "))

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
  def testMergedOverridingMarks(): Unit = doTest(
    s"""
       |trait Foo { def foo: Int; def bar: Int }
       |case class Bar(foo: Int, bar: Int) extends Foo$caret
     """.stripMargin
  )(testLineMarker("Multiple overriding members"))

  @Test
  def testSCL14208(): Unit = doTest(
   s"""
      |trait Foo {
      |  val a: Int$caret
      |}
      |class Bar extends Foo {
      |  override val a: Int = 42
      |}
    """.stripMargin
  )(testLineMarker("Member has overrides"))

  def testTypeAliasOverridesNothing(): Unit = {
    doTest(
      s"""trait T {
         |  type S = String$caret
         |}
         |""".stripMargin
    )(doTestNoLineMarkers())
  }

  def testTypeAliasOverridesNothing_1(): Unit = {
    doTest(
      s"""trait Base {
         |  type P = Int
         |}
         |
         |trait T extends Base {
         |  type S = String$caret
         |}
         |""".stripMargin
    )(doTestNoLineMarkers())
  }


  def testTypeAliasOverrides(): Unit = {
    doTest(
      s"""trait Base {
         |  type S = Int
         |}
         |
         |trait T extends Base {
         |  type S = String$caret
         |}
         |""".stripMargin
    )(testOverridesMarker("Base", "S", "type")("S in Base"))
  }
}
