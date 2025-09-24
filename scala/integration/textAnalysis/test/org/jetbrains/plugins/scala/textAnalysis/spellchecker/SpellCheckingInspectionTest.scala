package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class SpellCheckingInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[? <: LocalInspectionTool] = classOf[GrazieSpellCheckingInspection]

  override protected def description: String = null

  override protected def descriptionMatches(s: String): Boolean = s.contains("Typo:")

  def testOneLinePlain(): Unit = checkTextHasError(
    s"""println("Hello ${START}Abcdef$END world")
       |println("Hello ${START}Abcdef$END world\\n")
       |println("Hello ${START}Abcdef$END world\\\\")
       |println("Hello ${START}Abcdef$END world\\\\\\\\")
       |println("Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\")
       |""".stripMargin
  )

  def testOneLineInterpolatorS(): Unit = checkTextHasError(
    s"""println(s"Hello ${START}Abcdef$END world")
       |println(s"Hello ${START}Abcdef$END world\\n")
       |println(s"Hello ${START}Abcdef$END world\\\\")
       |println(s"Hello ${START}Abcdef$END world\\\\\\\\")
       |println(s"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\")
       |""".stripMargin
  )

  def testOneLineInterpolatorRaw(): Unit = checkTextHasError(
    s"""println(raw"Hello ${START}Abcdef$END world")
       |println(raw"Hello ${START}Abcdef$END world\\n")
       |println(raw"Hello ${START}Abcdef$END world\\\\")
       |println(raw"Hello ${START}Abcdef$END world\\\\\\\\")
       |println(raw"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\")
       |""".stripMargin
  )

  def testMultiLinePlain(): Unit = checkTextHasError(
    s"""println(\"\"\"Hello ${START}Abcdef$END world\"\"\")
       |println(\"\"\"Hello ${START}Abcdef$END world\\n\"\"\")
       |println(\"\"\"Hello ${START}Abcdef$END world\\\\\"\"\")
       |println(\"\"\"Hello ${START}Abcdef$END world\\\\\\\\\"\"\")
       |println(\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\\"\"\")
       |""".stripMargin
  )

  def testMultiLineInterpolatorS(): Unit = checkTextHasError(
    s"""println(s\"\"\"Hello ${START}Abcdef$END world\"\"\")
       |println(s\"\"\"Hello ${START}Abcdef$END world\\n\"\"\")
       |println(s\"\"\"Hello ${START}Abcdef$END world\\\\\"\"\")
       |println(s\"\"\"Hello ${START}Abcdef$END world\\\\\\\\\"\"\")
       |println(s\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\\"\"\")
       |""".stripMargin
  )

  def testMultiLineInterpolatorRaw(): Unit = checkTextHasError(
    s"""println(raw\"\"\"Hello ${START}Abcdef$END world\"\"\")
       |println(raw\"\"\"Hello ${START}Abcdef$END world\\n\"\"\")
       |println(raw\"\"\"Hello ${START}Abcdef$END world\\\\\"\"\")
       |println(raw\"\"\"Hello ${START}Abcdef$END world\\\\\\\\\"\"\")
       |println(raw\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\\"\"\")
       |""".stripMargin
  )

  def testMultiLineMultipleLinesPlain(): Unit = checkTextHasError(
    s"""println(
       |  \"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |\"\"\".stripMargin
       |)
       |""".stripMargin
  )

  def testMultiLineMultipleLinesInterpolatorS(): Unit = checkTextHasError(
    s"""println(
       |  s\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |\"\"\".stripMargin
       |)
       |""".stripMargin
  )

  def testMultiLineMultipleLinesInterpolatorRaw(): Unit = checkTextHasError(
    s"""println(
       |  raw\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    |\"\"\".stripMargin
       |)
       |""".stripMargin
  )


  def testMultiLineMultipleLinesWithoutStripMarginPlain(): Unit = checkTextHasError(
    s"""println(
       |  \"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |  \"\"\"
       |)
       |""".stripMargin
  )

  def testMultiLineMultipleLinesWithoutStripMarginInterpolatorS(): Unit = checkTextHasError(
    s"""println(
       |  s\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |  \"\"\"
       |)
       |""".stripMargin
  )

  def testMultiLineMultipleLinesWithoutStripMarginInterpolatorRaw(): Unit = checkTextHasError(
    s"""println(
       |  raw\"\"\"Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |    Hello \\\\\\\\\\\\\\\\ ${START}Abcdef$END world\\\\\\\\
       |  \"\"\"
       |)
       |""".stripMargin
  )

  def testDontIgnoreScalaDocTag(): Unit = checkTextHasError(
    s"""/**
      | * @note ${START}Mispeled$END ${START}Texxt$END
      | */
      |""".stripMargin
  )

  def testIgnoreScalaDocTag_See(): Unit = checkTextHasNoErrors(
    """/**
      | * @see Mispeled Texxt [[Mispeled Texxt]]
      | */
      |""".stripMargin
  )

  def testIgnoreScalaDocTag_Author(): Unit = checkTextHasNoErrors(
    """/**
      | * @author Mispeled Texxt
      | */
      |""".stripMargin
  )
}
