package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class IntegerLiteralCheckTestBase(supportsOctal: Boolean,
                                           supportsUnderscore: Boolean,
                                           supportsBinary: Boolean)
  extends ScalaLightCodeInsightFixtureTestCase with AssertionMatchers
{
  def testFine(): Unit = {
    val makeUnderscores: String => Iterator[String] =
      if (supportsUnderscore) padWithRandomUnderscores(5)
      else Iterator(_)

    val intStrings = (PredefinedInts ++ randomInts)
      .flatMap(integerRepresentations)
      .flatMap(makeUnderscores)
      .flatMap(prependSign)

    val longStrings = intStrings ++
      (PredefinedLongs ++ randomLongs)
        .flatMap(longRepresentations)
        .flatMap(makeUnderscores)
        .flatMap(prependSign)
        .flatMap(appendL)

    val allStrings = intStrings ++ longStrings

    checkTextHasNoErrors(allStrings.mkString("\n"))
  }

  def highlightingInfos(text: String): Seq[(String, String)] = {
    myFixture.configureByText(ScalaFileType.INSTANCE, text)
    myFixture.doHighlighting().asScala.toSeq
      .filter(_.getDescription != null)
      .map(e => e.getText -> e.getDescription)
  }

  def testLiteralOverflowInt(): Unit = {
    val longStrings = PredefinedLongs.map(_.toString) ++
      randomLongs.flatMap(longRepresentations)

    val messages =
      highlightingInfos(
        s"""
          |object Test {
          |${longStrings.mkString("\n")}
          |}
          |""".stripMargin
      )

    val expected = longStrings.map(_ -> ScalaBundle.message("integer.literal.is.out.of.range")).toSeq

    messages.sorted.mkString("\n") shouldBe expected.sorted.mkString("\n")
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

    val allStrings = overflowLongStrings ++ overflowLongStringsWithL ++ Seq("9223372036854775808l", "-9223372036854775809l")


    val messages =
      highlightingInfos(
        s"""
           |object Test {
           |${allStrings.mkString("\n")}
           |}
           |""".stripMargin
      )

    val expected = allStrings.map(_ -> ScalaBundle.message("long.literal.is.out.of.range")).toSeq

    messages.sorted.mkString("\n") shouldBe expected.sorted.mkString("\n")
  }

  def testOctalNum(): Unit = {
    val text ="01234567"

    if (supportsOctal) {
      highlightingInfos(text) shouldBe Seq.empty
    } else {
      highlightingInfos(text) shouldBe Seq(
        text -> ScalaBundle.message("octal.literals.removed")
      )
    }
  }

  def testUnderscores(): Unit = {
    val text = "100_000"

    if (supportsUnderscore) {
      highlightingInfos(text) shouldBe Seq.empty
    } else {

      highlightingInfos(text) shouldBe Seq(
        text -> ScalaBundle.message("illegal.underscore.separator")
      )
    }
  }

  def testBinary(): Unit = {
    val text = "0b101010"

    if (supportsBinary) {
      highlightingInfos(text) shouldBe Seq.empty
    } else {

      highlightingInfos(text) shouldBe Seq(
        text -> ScalaBundle.message("binary.literals.not.supported")
      )
    }
  }

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
    representations(int.toString, int.toOctalString, int.toHexString, int.toBinaryString)

  def longRepresentations(long: Long): List[String] =
    representations(long.toString, long.toOctalString, long.toHexString, long.toBinaryString)

  def prependSign(s: String): List[String] = s ::
    (if (s.startsWith("-") || s.startsWith("+")) Nil else "-" + s :: "+" + s :: Nil)

  def appendL(s: String): List[String] = s + "l" ::
    s + "L" ::
    Nil

  private def randomValues[T](generator: => T)
                                   (predicate: T => Boolean = Function.const(true)(_: T)) =
    LazyList
      .continually(generator)
      .filter(predicate)
      .take(10)

  def padWithRandomUnderscores(n: Int)(s: String): Iterator[String] =
    Iterator(s) ++
      Iterator.continually(s)
        .map(padWithRandomUnderscores)
        .take(n)

  private def padWithRandomUnderscores(s: String): String = {
    val minIdx = if (s.startsWith("-") || s.startsWith("+")) 2 else 1
    if (minIdx == s.length)
      return s
    val indices =
      Seq.fill(nextInt(s.length))(minIdx + nextInt(s.length - minIdx))
        .sortBy(-_)

    indices.foldLeft(s) {
      case (acc, index) if acc(index).isDigit => acc.patch(index, "_", 0)
      case (acc, _) => acc
    }
  }

  private def representations(decimalString: String,
                              octalString: String,
                              hexString: String,
                              binaryString: String
                             ): List[String] = {
    def whenSupported(s: String, supported: Boolean): List[String] =
      if (supported) s :: Nil else Nil
    decimalString ::
      whenSupported("0" + octalString, supportsOctal) :::
      "0x" + hexString ::
      "0X" + hexString ::
      whenSupported("0b" + binaryString, supportsBinary) :::
      whenSupported("0B" + binaryString, supportsBinary) :::
      Nil
  }
}

class IntegerLiteralCheckTest2_10 extends IntegerLiteralCheckTestBase(supportsOctal = true, supportsUnderscore = false, supportsBinary = false) {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_10
}

class IntegerLiteralCheckTest2_12 extends IntegerLiteralCheckTestBase(supportsOctal = false, supportsUnderscore = false, supportsBinary = false) {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class IntegerLiteralCheckTest2_13 extends IntegerLiteralCheckTestBase(supportsOctal = false, supportsUnderscore = true, supportsBinary = true) {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class IntegerLiteralCheckTest3_4 extends IntegerLiteralCheckTestBase(supportsOctal = false,supportsUnderscore = true, supportsBinary = false) {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class IntegerLiteralCheckTest3_5 extends IntegerLiteralCheckTestBase(supportsOctal = false,supportsUnderscore = true, supportsBinary = false /* TODO: change to true in Scala 3.5 */) {
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3 // TODO: change to Scala_3_5 if available
}