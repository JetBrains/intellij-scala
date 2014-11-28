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
    List.fill(num)(Random.nextInt)
  }

  def randomLongValues(num: Int): List[Long] = {
    Stream.continually(Random.nextLong).filter(x => x > Int.MaxValue || x < Int.MinValue).take(num).toList
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

  def appendL(ss: String): List[String] = List(ss + "l", ss + "L")

  val intValues = List(0, 1, 1234, Int.MaxValue, Int.MinValue)
  val longValues = List(1l + Int.MaxValue, 1234l + Int.MaxValue, Long.MaxValue, Long.MinValue)
  val numOfGenInteger = 10

  def testFine(): Unit = {
    val intStrings = ((intValues ++ randomIntValues(numOfGenInteger)) flatMap expandIntegerLiteral flatMap prependSign).distinct
    for (s <- intStrings) {
      assertNothing(messages(s"val a = $s"))
    }
    val longStrings = (intStrings flatMap appendL) ++
                      ((longValues ++ randomLongValues(numOfGenInteger))  flatMap expandIntegerLiteral flatMap prependSign flatMap appendL).distinct
    for (s <- longStrings) {
      assertNothing(messages(s"val a = $s"))
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

//      mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
    mock.annotations
  }

}
