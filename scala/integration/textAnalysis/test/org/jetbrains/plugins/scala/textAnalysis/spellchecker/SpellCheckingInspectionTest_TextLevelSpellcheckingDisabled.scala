package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

//noinspection SpellCheckingInspection (Mute spell check inspeciton as the test data is supposed to sometime has the errors)
abstract class SpellCheckingInspectionTestBase extends ScalaInspectionTestBase {
  protected def textLevelSpellcheckingEnabled: Boolean

  protected override def setUp(): Unit = {
    super.setUp()
    Registry.get("spellchecker.grazie.enabled").setValue(textLevelSpellcheckingEnabled, getTestRootDisposable)
  }

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

  def testEscapeNewLine_SingleLineString(): Unit = {
    checkTextHasNoErrors(
      """val value = "wrong object class\nexpected 1\nactual: 2"
        |""".stripMargin
    )
  }

  def testEscapeNewLine_SingleLineString_Interpolated_S(): Unit = {
    checkTextHasNoErrors(
      """val value = s"wrong object class\nexpected 1\nactual: 2"
        |""".stripMargin
    )
  }

  def testEscapeNewLine_SingleLineString_Interpolated_Raw(): Unit = {
    checkTextHasError(
      s"""val value = raw"wrong object class\\${START}nexpected$END 1\\${START}nactual$END: 2"
        |""".stripMargin
    )
  }

  def testEscapeNewLine_MultiLineString(): Unit = {
    checkTextHasError(
      s"""val value = ""\"wrong object class\\${START}nexpected$END 1\\${START}nactual$END: 2""\"
        |""".stripMargin
    )
  }

  def testEscapeNewLine_MultiLineString_Interpolated_S(): Unit = {
    checkTextHasNoErrors(
      s"""val value = s""\"wrong object class\\nexpected 1\\nactual: 2""\"
        |""".stripMargin
    )
  }

  def testEscapeNewLine_MultiLineString_Raw(): Unit = {
    checkTextHasError(
      s"""val value = raw""\"wrong object class\\${START}nexpected$END 1\\${START}nactual$END: 2""\"
         |""".stripMargin
    )
  }

  def testInvalidEscapeSequence(): Unit = {
    // We gracefully handle incorrect escape sequences mostly for custom string interpolators (SCL-25082)
    checkTextHasError(
      s"""val value = s"${START}Mispeled$END \\   ${START}Mispeled$END \\x   ${START}Mispeled$END"
         |val value = custom"${START}Mispeled$END \\   ${START}Mispeled$END \\x   ${START}Mispeled$END"
         |""".stripMargin
    )
  }
}

final class SpellCheckingInspectionTest_TextLevelSpellcheckingDisabled extends SpellCheckingInspectionTestBase {
  override def textLevelSpellcheckingEnabled: Boolean = false
}

final class SpellCheckingInspectionTest_TextLevelSpellcheckingEnabled extends SpellCheckingInspectionTestBase {
  override def textLevelSpellcheckingEnabled: Boolean = true
}
