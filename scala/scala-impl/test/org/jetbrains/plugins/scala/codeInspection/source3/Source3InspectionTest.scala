package org.jetbrains.plugins.scala.codeInspection.source3

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class Source3InspectinTestBase(source3Flag: String) extends ScalaInspectionTestBase {
  override def setUp(): Unit = {
    super.setUp()
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(myFixture.getProject).defaultProfile
    val newSettings =
      defaultProfile.getSettings.copy(
        additionalCompilerOptions = Seq(source3Flag)
      )
    defaultProfile.setSettings(newSettings)
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13.withMinor(6)

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[Source3Inspection]

  override protected val description = "Scala 2 syntax with -Xsource:3"

  def test_wildcard_replacement(): Unit = {
    val selectedText = s"val x: ${START}_$END <: Any = 1"
    checkTextHasError(selectedText)

    testQuickFix(
      "val x: _ <: Any = null",
      "val x: ? <: Any = null",
      "Replace with ?"
    )
  }

  def test_case_for_pattern(): Unit = {
    val selectedText = s"for { ${START}1$END <- Seq(1, 2) } ()"
    checkTextHasError(selectedText)

    testQuickFix(
      "for { 1 <- Seq(1, 2) } ()",
      "for {case 1 <- Seq(1, 2)} ()",
      "Add 'case'"
    )
  }

  def test_case_not_needed_for_irrefutable_pattern(): Unit = {
    val selectedText = s"for { i <- Seq(1, 2) } ()"
    checkTextHasNoErrors(selectedText)
  }

  def test_case_not_needed_for_irrefutable_pattern_tuple(): Unit = {
    val selectedText = s"for { (a, b) <- Seq((1, 2)) } ()"
    checkTextHasNoErrors(selectedText)
  }

  // SCL-19204
  def test_yield(): Unit =
    checkTextHasNoErrors(
      """
      |for {
      |  (a, b) <- returnTuple
      |  _ = println(a)
      |  _ = println(b)
      |} yield ()
      |""".stripMargin
    )

  def test_wildcard_import(): Unit = {
    val selectedText = s"import base.${START}_$END"
    checkTextHasError(selectedText)

    testQuickFix(
      "import base._",
      "import base.*",
      "Replace with *"
    )
  }

  def test_wildcard_import_in_selector(): Unit = {
    val selectedText = s"import base.{nope, ${START}_$END}"
    checkTextHasError(selectedText)

    testQuickFix(
      "import base.{nope, _}",
      "import base.{nope, *}",
      "Replace with *"
    )
  }

  def test_underscore_is_shadowing(): Unit = {
    val selectedText = s"import base.{x as _}"
    checkTextHasNoErrors(selectedText)
  }

  def test_arrow_to_as(): Unit = {
    val selectedText = s"import base.{x $START=>$END y}"
    checkTextHasError(selectedText)

    testQuickFix(
      "import base.{x => y}",
      "import base.{x as y}",
      "Replace with 'as'"
    )
  }

  def test_vararg_slices(): Unit = {
    val selectedText = s"Seq(a: ${START}_*$END)"
    checkTextHasError(selectedText)

    testQuickFix(
      "Seq(a: _*)",
      "Seq(a *)",
      "Replace with *"
    )
  }

  def test_infix_vararg_slices(): Unit = {
    val selectedText = s"Seq(a: ${START}_*$END)"
    checkTextHasError(selectedText)

    testQuickFix(
      "Seq(a ++ a: _*)",
      "Seq((a ++ a) *)",
      "Replace with *"
    )
  }

  def test_vararg_slices_undersocered(): Unit = {
    val selectedText = s"Seq(a: ${START}_*$END)"
    checkTextHasError(selectedText)

    testQuickFix(
      "Seq(a_ : _*)",
      "Seq(a_ *)",
      "Replace with *"
    )
  }

  def test_vararg_pattern(): Unit = {
    val selectedText = s"val Seq(${START}a@_*$END) = null"
    checkTextHasError(selectedText)

    testQuickFix(
      "val Seq(a@_*) = null",
      "val Seq(a*) = null",
      "Replace with 'a*'"
    )
  }

  def test_compound_type(): Unit = {
    val selectedText = s"val x: A ${START}with$END B = null"
    checkTextHasError(selectedText)

    testQuickFix(
      "val x: A with B = null",
      "val x: A & B = null",
      "Replace with &"
    )
  }

  def test_compound_type_in_pattern(): Unit = checkTextHasNoErrors("??? match { case x: A with B => }")
}

class Source3InspectionTest extends Source3InspectinTestBase("-Xsource:3")
class Source3CrossInspectionTest extends Source3InspectinTestBase("-Xsource:3-cross")