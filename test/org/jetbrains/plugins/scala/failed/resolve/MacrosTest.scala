package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 20/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class MacrosTest extends SimpleTestCase {

  def doResolveTest (code: String) {
    val (psi, caretPos) = parseText(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ResolvableReferenceElement => assert(r.resolve() != null, "failed to resolve enclosing object")
      case _ => assert(true)
    }
  }

  def testSCL8414a(): Unit = {
    doResolveTest(
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
    doResolveTest(
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

}
