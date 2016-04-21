package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class PatternResolveTest extends SimpleTestCase {

  def doResolveTest(code: String) {
    val (psi, caretPos) = parseText(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ResolvableReferenceElement => assert(r.resolve() != null, "failed to resolve enclosing object")
      case _ => assert(true)
    }
  }

  def testSCL5895(): Unit = {
    doResolveTest(
      """
        |  case class Bar[T](wrapped: T) {
        |    def method(some: T) = ???
        |  }
        |
        |  def bar(fooTuple: (Bar[T], T) forSome { type T }) = fooTuple match {
        |    case (a, b) => a.<caret>method(b)
        |  }
      """.stripMargin)

  }
}
