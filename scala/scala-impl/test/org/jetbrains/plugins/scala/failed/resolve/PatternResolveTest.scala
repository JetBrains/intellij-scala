package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Anton Yalyshev on 21/04/16.
  */
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

  def testSCL11155(): Unit = {
    doResolveCaretTest(
      """
        |import java.security.MessageDigest
        |
        |import scala.math.max
        |
        |object D17 {
        |
        |  val md: MessageDigest = MessageDigest.getInstance("MD5")
        |  val input: Array[Byte] = "udskfozm".getBytes
        |
        |  def main(args: Array[String]) {
        |    println(find(0, 0, ""))
        |  }
        |
        |  implicit def find(curr: (Int, Int, String)): Int = curr match {
        |    case (3, 3, d) => d.length
        |    case (x, y, d) => md.digest(input ++ d.getBytes).take(2).map("%02x" format _).mkString.zipWithIndex
        |      .filter(_._1 > 'a')
        |      .foldLeft(0)((m, i) => <caret>max(m, i._2 match {
        |        case 0 if x > 0 => (x - 1, y, d + 'U')
        |        case 1 if x < 3 => (x + 1, y, d + 'D')
        |        case 2 if y > 0 => (x, y - 1, d + 'L')
        |        case 3 if y < 3 => (x, y + 1, d + 'R')
        |        case _ => 0
        |      }))
        |  }
        |
        |}
      """.stripMargin)
  }

  def testSCL11156a(): Unit = {
    doResolveCaretTest(
      """
        |trait Parser[T] extends Function[String, Option[(T, String)]]
        |
        |object Parser {
        |  val item: Parser[Char] = {
        |    case "" => None
        |    case v => Some((v.<caret>charAt(0), v.substring(1)))
        |  }
        |}
      """.stripMargin)
  }

  def testSCL11156b(): Unit = {
    doResolveCaretTest(
      """
        |trait Parser[T] extends Function[String, Option[(T, String)]]
        |
        |object Parser {
        |  val item: Parser[Char] = {
        |    case "" => None
        |    case v => Some((v.charAt(0), v.<caret>substring(1)))
        |  }
        |}
      """.stripMargin)
  }

  def testSCL13148(): Unit = {
    doResolveCaretTest(
      """
        |trait A[T <: Comparable[T]]
        |
        |object X {
        |  def f(x : Any): Any = x match { case _ : A[t] => (y : t, z : t) => y.<caret>compareTo(z) }
        |}
      """.stripMargin)
  }
}
