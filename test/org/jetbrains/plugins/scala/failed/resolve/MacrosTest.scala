package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 20/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class MacrosTest extends FailedResolveCaretTestBase {

  def testSCL8414a(): Unit = {
    doResolveCaretTest(
      """
        |import scala.language.experimental.macros
        |import scala.reflect.macros.blackbox.Context
        |class Impl(val c: Context) {
        |  def mono = c.literalUnit
        |  def poly[T: c.WeakTypeTag] = c.literal(c.weakTypeOf[T].toString)
        |}
        |object Macros {
        |  def mono = macro <caret>Impl.mono
        |}
      """.stripMargin)
  }

  def testSCL8414b(): Unit = {
    doResolveCaretTest(
      """
        |import scala.language.experimental.macros
        |import scala.reflect.macros.blackbox.Context
        |class Impl(val c: Context) {
        |  def mono = c.literalUnit
        |  def poly[T: c.WeakTypeTag] = c.literal(c.weakTypeOf[T].toString)
        |}
        |object Macros {
        |  def poly[T] = macro <caret>Impl.poly[T]
        |}
      """.stripMargin)
  }

  def testSCL8507(): Unit = doResolveCaretTest(
    s"""
       |object x extends App {
       |  import shapeless._
       |  case class Foo(i: Int, s: String, b:Boolean)
       |  val foo = Foo(23, "foo", true)
       |
       |  val gen = Generic[Foo]
       |  val hfoo = gen.to(foo)
       |
       |  val foo2 = gen.from(hfoo.<caret>head :: "bar" :: hfoo.tail.tail)
       |
       |  println(foo2)
       |}
    """.stripMargin)
}
