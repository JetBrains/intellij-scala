package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

import scala.util.Random


/**
 * @author Ye Xianjin
 * @since  11/27/14
 */
class IntegerLiteralCheckTest extends SimpleTestCase {
  final val Header = ""

  def randomIntValues(num: Int): List[Int] = {
    Int.MaxValue :: Int.MinValue :: List.fill(num)(Random.nextInt)
  }

  def randomLongValues(num: Int): List[Long] = {
    val randomList = Stream.continually(Random.nextLong).filter(_.toHexString.length > 8).take(num).toList
    Long.MaxValue :: Long.MinValue :: randomList
  }

  // how should I bound T to Int and Long only?
  def expandIntegerLiteral[T](x: T): List[String] = {
    val (octalString, hexString) = x match {
      case t: Int => (java.lang.Integer.toOctalString(t), java.lang.Integer.toHexString(t))
      case t: Long => (java.lang.Long.toOctalString(t), java.lang.Long.toHexString(t))
    }
    List(x.toString, "0" + octalString, "0x" + hexString, "0X" + hexString)
  }

  def prependSign(s: String): List[String] = if (s.startsWith("-")) List(s) else List(s, "-" + s)

  def appendL(s: String): List[String] = List(s + "l", s + "L")

  val intValues = List(0, -0, 1, -1, 1234, -1234)
  val longValues = List(1l + Int.MaxValue, 12345l + Int.MaxValue, -1l +  Int.MinValue, -1234l + Int.MinValue)
  val numOfGenInteger = 10

  def testFine() {
    val intStrings = ((intValues ++ randomIntValues(numOfGenInteger)).flatMap(expandIntegerLiteral).flatMap(prependSign)).distinct
    for (s <- intStrings) {
      assertNothing(messages(s"val a = $s"))
    }
    val longStrings = (intStrings flatMap appendL) ++
                      ((longValues ++ randomLongValues(numOfGenInteger)).flatMap(expandIntegerLiteral).flatMap(prependSign).flatMap(appendL)).distinct
    for (s <- longStrings) {
      assertNothing(messages(s"val a = $s"))
    }
  }

  def testLiteralOverflowInt() {
    val longStrings = longValues.map(_.toString) ++ randomLongValues(numOfGenInteger).flatMap(expandIntegerLiteral).distinct
    for (s <- longStrings) {
      assertMatches(messages(s"val a = $s")) {
        case Error(s, OverflowIntPattern()) :: Nil =>
      }
    }
  }

  def testLiteralOverflowLong() {
    val overflowLongStrings = (longValues ++ randomLongValues(numOfGenInteger)).
                              flatMap(x => List(x.toString.padTo(21, '1'), "0x" + x.toHexString.padTo(17, '1'), "0" + x.toOctalString.padTo(23, '1')))
    val overflowLongStringsWithL = overflowLongStrings.flatMap(appendL)
    for (s <- overflowLongStrings ++ overflowLongStringsWithL) {
      assertMatches(messages(s"val a = $s")) {
        case Error(s, OverflowLongPattern()) :: Nil =>
      }
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
      val annotator = new ScalaAnnotator() {}
      val mock = new AnnotatorHolderMock

      val parse: ScalaFile = (Header + code).parse

      parse.depthFirst.foreach {
        case literal: ScLiteral => annotator.annotate(literal, mock)
        case _ =>
      }

      mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
  }

  val OverflowIntPattern = ContainsPattern("out of range for type Int")
  val OverflowLongPattern = ContainsPattern("out of range even for type Long")
  case class ContainsPattern(fragment: String) {
      def unapply(s: String) = s.contains(fragment)
    }
}
