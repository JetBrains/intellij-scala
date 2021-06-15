package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api._

/**
 * @author Ye Xianjin
 * @since 11/27/14
 */
class IntegerLiteralCheckTest extends AnnotatorSimpleTestCase {

  import IntegerLiteralCheckTest._

  private val OverflowIntPattern = BundleMessagePattern(ScalaBundle.message("integer.literal.is.out.of.range"))
  private val OverflowLongPattern = BundleMessagePattern(ScalaBundle.message("long.literal.is.out.of.range"))

  def testFine(): Unit = {
    val intStrings = (PredefinedInts ++ randomInts)
      .flatMap(integerRepresentations)
      .flatMap(prependSign)

    for {
      literalText <- intStrings
      actual = messages(literalText)
    } assertNothing(actual)

    val longStrings = intStrings ++
      (PredefinedLongs ++ randomLongs)
        .flatMap(longRepresentations)
        .flatMap(prependSign)
    for {
      literalText <- longStrings
      longLiteralText <- appendL(literalText)
      actual = messages(longLiteralText)
    } assertMatches(actual) {
      case OptionalWarning() =>
    }
  }

  def testLiteralOverflowInt(): Unit = {
    val longStrings = PredefinedLongs.map(_.toString) ++
      randomLongs.flatMap(longRepresentations)

    for {
      literalText <- longStrings
      actual = messages(literalText)
    } assertMatches(actual) {
      case Error(`literalText`, OverflowIntPattern()) :: OptionalWarning() =>
    }
  }

  def testLiteralOverflowLong(): Unit = {
    val overflowLongStrings = (PredefinedLongs ++ randomLongs)
      .flatMap { long =>
        longRepresentations(long)
          .zip {
            21 :: 24 :: 19 :: Nil
          }.map {
          case (string, length) => string.padTo(length, '1')
        }
      }
    val overflowLongStringsWithL = overflowLongStrings
      .flatMap(appendL)

    for {
      literalText <- overflowLongStrings ++ overflowLongStringsWithL ++ Seq("9223372036854775808l", "-9223372036854775809l")
      actual = messages(literalText)
    } assertMatches(actual) {
      case Error(`literalText`, OverflowLongPattern()) :: OptionalWarning() =>
    }
  }

  private def messages(literalText: String): List[Message] = {
    val annotator = ScalaAnnotator.forProject
    val file: ScalaFile = ("val x = " + literalText).parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst()
      .filter(_.isInstanceOf[base.ScLiteral.Numeric])
      .foreach(annotator.annotate)

    mock.annotations
      .filterNot(_.isInstanceOf[Info])
  }

  private object OptionalWarning {

    private val LowerCaseLongMarkerPattern = BundleMessagePattern(ScalaBundle.message("lowercase.long.marker"))

    def unapply(list: List[Message]): Boolean = list match {
      case Nil => true
      case Warning(element, LowerCaseLongMarkerPattern()) :: Nil => element.endsWith("l")
      case _ => false
    }
  }
}

//noinspection TypeAnnotation
private object IntegerLiteralCheckTest {

  import java.lang.Integer.{MAX_VALUE => IntMaxValue, MIN_VALUE => IntMinValue}
  import java.lang.Long.{MAX_VALUE => LongMaxValue, MIN_VALUE => LongMinValue}

  import scala.util.Random._

  val PredefinedInts = Set(
    0,
    -0,
    1,
    -1,
    1234,
    -1234,
    IntMinValue,
    IntMaxValue
  )

  val PredefinedLongs = Set(
    1L + IntMaxValue,
    12345L + IntMaxValue,
    -1L + IntMinValue,
    -1234L + IntMinValue,
    LongMinValue,
    LongMaxValue
  )

  def randomInts: LazyList[Int] = randomValues(nextInt())()

  def randomLongs: LazyList[Long] = randomValues(nextLong()) {
    _.toHexString.length > 8
  }

  def integerRepresentations(int: Int): List[String] =
    representations(int.toString, int.toOctalString, int.toHexString)

  def longRepresentations(long: Long): List[String] =
    representations(long.toString, long.toOctalString, long.toHexString)

  def prependSign(s: String): List[String] = s ::
    (if (s.startsWith("-")) Nil else "-" + s :: Nil)

  def appendL(s: String): List[String] = s + "l" ::
    s + "L" ::
    Nil

  private[this] def randomValues[T](generator: => T)
                                   (predicate: T => Boolean = Function.const(true)(_: T)) =
    LazyList
      .continually(generator)
      .filter(predicate)
      .take(10)

  private[this] def representations(decimalString: String,
                                    octalString: String,
                                    hexString: String) =
    decimalString ::
      "0" + octalString ::
      "0x" + hexString ::
      "0X" + hexString ::
      Nil
}
