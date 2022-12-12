package org.jetbrains.plugins.scala.codeInsight.declarationRedundancy

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.worksheet.WorksheetLanguage

import scala.jdk.CollectionConverters.CollectionHasAsScala

class WorksheetInspectionTest extends ScalaLightCodeInsightFixtureTestCase {

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  private def getInfos(code: String): Seq[HighlightInfo] = {
    val vFile = ScratchRootType.getInstance.createScratchFile(getProject, "foo.sc", ScalaLanguage.INSTANCE, code)
    ScratchFileService.getInstance.getScratchesMapping.setMapping(vFile, WorksheetLanguage.INSTANCE)
    myFixture.configureFromExistingVirtualFile(vFile)
    val allHighlightInfos = myFixture.doHighlighting().asScala
    allHighlightInfos.filter(_.getDescription == ScalaInspectionBundle.message("access.can.be.private")).toSeq
  }

  private def assertHighlightedElementsTexts(
    code: String,
    expectedHighlightedElementsTexts: Seq[String]
  ): Unit = {
    val infos = getInfos(code)

    val actualHighlightedElementsTexts = infos.map(_.getText)

    assertCollectionEquals(
      "Highlighted element texts are different",
      expectedHighlightedElementsTexts.sorted,
      actualHighlightedElementsTexts.sorted
    )
  }

  def test_top_level_definition(): Unit = {
    assertHighlightedElementsTexts(
      "class Bar; new Bar()",
      Nil
    )
  }

  def test_non_top_level_definition(): Unit = {
    assertHighlightedElementsTexts(
      """object WorksheetInspectionTest {
        |  object WorksheetInspectionTestInner {
        |    val iCouldEasilyBePrivate = 42
        |    println(iCouldEasilyBePrivate)
        |  }
        |  println(WorksheetInspectionTestInner)
        |}
        |""".stripMargin,
      Seq("WorksheetInspectionTestInner", "iCouldEasilyBePrivate")
    )
  }
}
