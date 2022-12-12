package org.jetbrains.plugins.scala.codeInsight.declarationRedundancy

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.worksheet.WorksheetLanguage
import org.junit.Assert.assertTrue

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

  def test_top_level_definition(): Unit = {
    val infos = getInfos("class Bar; new Bar()")
    assertTrue(s"${infos.size} highlights were found, expected 0", infos.isEmpty)
  }

  def test_non_top_level_definition(): Unit = {
    val code =
      """object WorksheetInspectionTest {
        |  object WorksheetInspectionTestInner {
        |    val iCouldEasilyBePrivate = 42
        |    println(iCouldEasilyBePrivate)
        |  }
        |  println(WorksheetInspectionTestInner)
        |}
        |""".stripMargin

    val infos = getInfos(code)

    assertTrue(s"${infos.size} highlights were found, expected 2", infos.size == 2)

    val highlightedDeclarations = infos.map(_.getText)

    val expectedDeclarations = Seq("WorksheetInspectionTestInner", "iCouldEasilyBePrivate")

    val msg = s"Highlighted element texts are ${highlightedDeclarations.mkString(" and ")}, expected " +
      expectedDeclarations.mkString(" and ")

    assertTrue(msg, expectedDeclarations.forall(highlightedDeclarations.contains))
  }
}
