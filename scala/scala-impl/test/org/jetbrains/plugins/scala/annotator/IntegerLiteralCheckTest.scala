package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class IntegerLiteralCheckTestBase(supportsOctal: Boolean,
                                           supportsUnderscore: Boolean,
                                           supportsBinary: Boolean,
                                           supports0Prefix: Boolean)
  extends ScalaLightCodeInsightFixtureTestCase with AssertionMatchers
{
  def test0(): Unit = checkTextHasNoErrors("0")

  def testFine(): Unit = {
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

  def testIntValues(): Unit = {
    val groups = (PredefinedInts ++ randomInts)
      .filter(_ >= 0)
      .map { i =>
        i -> Seq(i)
          .flatMap(integerRepresentations)
          .flatMap(padWith0)
          .flatMap(makeUnderscores)
      }.to(SortedMap)

    doLiteralValueTest(groups)
  }

  def testLongValues(): Unit = {
    val groups = (PredefinedLongs ++ randomLongs)
      .filter(_ >= 0)
      .map { i =>
        i -> Seq(i)
          .flatMap(longRepresentations)
          .flatMap(padWith0)
          .flatMap(makeUnderscores)
          .flatMap(appendL)
      }.to(SortedMap)

    doLiteralValueTest(groups)
  }

  private def doLiteralValueTest[N: Numeric](groups: SortedMap[N, Seq[String]]): Unit = {
    val numberStrings = groups.values.flatten.toSeq

    val text = numberStrings.mkString("\n")
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, text).asInstanceOf[ScalaFile]
    val literals = file.depthFirst().collect {
      case e: ScLiteral.Numeric => e
    }.toSeq

    literals.length shouldBe numberStrings.length

    val expected = groups.flatMap { case (n, strings) =>
      strings.map(s => s"$s -> $n")
    }.mkString("\n")

    val literalIt = literals.iterator
    val actual = groups.flatMap { case (_, strings) =>
      strings.map { _ =>
        val literal = literalIt.next()
        s"${literal.getText} -> ${literal.getValue}"
      }
    }.mkString("\n")

    actual shouldBe expected
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

  private def randomInts: LazyList[Int] = randomValues(nextInt())()

  private def randomLongs: LazyList[Long] = randomValues(nextLong()) {
    _.toHexString.length > 8
  }

  private def integerRepresentations(int: Int): List[String] =
    representations(int.toString, int.toOctalString, int.toHexString, int.toBinaryString)

  private def longRepresentations(long: Long): List[String] =
    representations(long.toString, long.toOctalString, long.toHexString, long.toBinaryString)

  private def prependSign(s: String): List[String] = s ::
    (if (s.startsWith("-") || s.startsWith("+")) Nil else "-" + s :: "+" + s :: Nil)

  private def appendL(s: String): List[String] = s + "l" ::
    s + "L" ::
    Nil

  private def randomValues[T](generator: => T)
                             (predicate: T => Boolean = Function.const(true)(_: T)) =
    LazyList
      .continually(generator)
      .filter(predicate)
      .take(10)

  private def padWithRandomUnderscores(n: Int)(s: String): Iterator[String] =
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

  private val makeUnderscores: String => Iterator[String] =
    if (supportsUnderscore) padWithRandomUnderscores(5)
    else Iterator(_)

  private def padWith0(s: String): Iterator[String] = {
    if (!supports0Prefix)
      return Iterator(s)

    if (s.lift(1).exists(!_.isDigit))
      return Iterator(s)

    Iterator(s, "0" + s, "00" + s)
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

class IntegerLiteralCheckTest2_10
  extends IntegerLiteralCheckTestBase(
    supportsOctal = true,
    supportsUnderscore = false,
    supportsBinary = false,
    supports0Prefix = false,
  )
{
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_10
}

class IntegerLiteralCheckTest2_12
  extends IntegerLiteralCheckTestBase(
    supportsOctal = false,
    supportsUnderscore = false,
    supportsBinary = false,
    supports0Prefix = false,
  )
{
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class IntegerLiteralCheckTest2_13
  extends IntegerLiteralCheckTestBase(
    supportsOctal = false,
    supportsUnderscore = true,
    supportsBinary = true,
    supports0Prefix = false,
  )
{
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class IntegerLiteralCheckTest3_4
  extends IntegerLiteralCheckTestBase(
    supportsOctal = false,
    supportsUnderscore = true,
    supportsBinary = false,
    supports0Prefix = true,
  )
{
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_4
}

class IntegerLiteralCheckTest3_5
  extends IntegerLiteralCheckTestBase(
    supportsOctal = false,
    supportsUnderscore = true,
    supportsBinary = false, // TODO: change to true in Scala 3.5
    supports0Prefix = true,
  )
{
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3 // TODO: change to Scala_3_5 if available
}