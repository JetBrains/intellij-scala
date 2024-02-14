package org.jetbrains.plugins.scala.codeInspection.format

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert

import java.io.File
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaMalformedFormatStringInspectionTest_2_11 extends ScalaMalformedFormatStringInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}
class ScalaMalformedFormatStringInspectionTest_2_12 extends ScalaMalformedFormatStringInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}
class ScalaMalformedFormatStringInspectionTest_2_13 extends ScalaMalformedFormatStringInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

// this inspection doesn't have quick fix, only test warning messages and positions
abstract class ScalaMalformedFormatStringInspectionTestBase extends ScalaInspectionTestBase {

  private val testDataFolder = new File(TestUtils.getTestDataPath + "/inspections/ScalaMalformedFormatStringInspection")
  assert(testDataFolder.exists())

  override protected val classOfInspection: Class[ScalaMalformedFormatStringInspection] =
    classOf[ScalaMalformedFormatStringInspection]

  private val inspectionToolId = classOfInspection.getDeclaredConstructor().newInstance().getID

  override protected val description: String = ""

  private def doTest(): Unit = {
    val testFile = new File(testDataFolder, getTestName(true) + ".test")
    assert(testFile.exists())

    val Seq(code, expectedInspections) =
      TestUtils.readInput(testFile)

    myFixture.configureByText("a.scala", code)

    val infos0 = myFixture.doHighlighting().asScala
    val infos = infos0.filter(_.getInspectionToolId == inspectionToolId)
    val infosText  = infos.map(infoText)
    val infosTextConcatenated = infosText.mkString("\n")

    Assert.assertEquals(expectedInspections, infosTextConcatenated)
  }

  private def infoText(info: HighlightInfo) = {
    val severity = info.getSeverity
    val attributeKeyName = info.`type`.getAttributesKey.getExternalName
    // don't want to duplicate `WARNING WARNING_ATTRIBUTES`
    // but would like to see `WARNING NOT_USED_ELEMENT_ATTRIBUTES`
    val attributeKeyDisplayed = if (attributeKeyName == s"${severity}_ATTRIBUTES") "" else " " + attributeKeyName
    s"$severity$attributeKeyDisplayed (${info.getStartOffset}, ${info.getEndOffset}) ${info.getDescription}"
  }

  def testPlainString(): Unit = doTest()
  def testPlainString_1(): Unit = doTest()
  def testPlainStringNoWarnings(): Unit = doTest()
  def testPlainStringNoWarnings_DifferentTypes(): Unit = doTest()
  def testMultilineString(): Unit = doTest()
  def testMultilineString_1(): Unit = doTest()
  def testMultilineStringNoWarnings(): Unit = doTest()
}