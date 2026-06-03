package org.jetbrains.plugins.scala.codeInspection.imports

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}


abstract class SingleImportInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[SingleImportInspection]

  override val description = ScalaInspectionBundle.message("single.import")

  def doTest(code: String, expe: String): Unit = {
    checkTextHasError(code)
    testQuickFix(
      code.replace(START, "").replace(END, ""),
      expe,
      ScalaBundle.message("remove.braces.from.import")
    )
  }

  def test_no_need(): Unit = checkTextHasNoErrors(
    "import test.x"
  )

  def test_one_selector(): Unit = doTest(
    s"import test.$START{x}$END",
    "import test.x"
  )

  def test_two_selector(): Unit = checkTextHasNoErrors(
    "import test.{x, y}"
  )

  def test_alias_scala2_style(): Unit = checkTextHasNoErrors(
    s"import test.{x => y}"
  )

}

class SingleImportInspectionTest_2 extends SingleImportInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version <= LatestScalaVersions.Scala_2_13

  def test_wildcard(): Unit = doTest(
    s"import test.$START{_}$END",
    "import test._"
  )

  def test_id_and_wildcard(): Unit = checkTextHasNoErrors(
    "import test.{x, _}"
  )
}


class SingleImportInspectionTest_3 extends SingleImportInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def test_alias(): Unit = doTest(
    s"import test.$START{x as y}$END",
    "import test.x as y"
  )

  def test_no_need_alias(): Unit = checkTextHasNoErrors(
    "import test.x as y"
  )

  def test_wildcard(): Unit = doTest(
    s"import test.$START{*}$END",
    "import test.*"
  )

  def test_given_selector(): Unit = doTest(
    s"import test.$START{given}$END",
    "import test.given"
  )

  def test_id_and_wildcard(): Unit = checkTextHasNoErrors(
    "import test.{x, *}"
  )
}
