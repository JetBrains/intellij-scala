package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 21/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
class PatternResolveTest extends FailedResolveCaretTestBase {

  def testSCL5895(): Unit = {
    doResolveCaretTest(
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

  def testSCL11097(): Unit = {
    doResolveCaretTest(
      """
        |  def pack(ls: List[_]): List[List[_]] = ls.foldRight(Nil: List[List[_]]) {
        |    (x, packed) => {
        |      if (packed.isEmpty || x != packed.head.head) List(x) +: packed
        |      else (x +: packed.head) +: packed.tail
        |    }
        |  }
        |
        |  def encode(ls: List[_]): List[(Int, _)] = pack(ls).map(l => (l.size, l.head))
        |
        |  def encodeModified(ls: List[_]): List[_] = encode(ls).map {
        |    case (l, elem) if l <caret>== 1 => elem
        |    case l => l
        |  }
      """.stripMargin)
  }
}
