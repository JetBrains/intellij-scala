package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.{InspectionEngine, InspectionManager, LocalInspectionEP, LocalInspectionTool, LocalInspectionToolSession, ProblemsHolder}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert.assertFalse

class GeneralInspectionSanityTest extends ScalaLightCodeInsightFixtureTestCase {

  def acquireAllInspectionEPs(): Seq[LocalInspectionEP] =
    LocalInspectionEP.LOCAL_INSPECTION
      .getExtensions()
      .toSeq

  def acquireAllScalaInspectionEPs(): Seq[LocalInspectionEP] =
    acquireAllInspectionEPs().filter(ep => ep.language == "Scala" || Option(ep.groupPath).exists(_.toLowerCase.contains("scala")))

  def getDescription(inspectionEP: LocalInspectionEP): String = {
    val description = new LocalInspectionToolWrapper(inspectionEP).loadDescription()
    assert(description != null, s"The description for the inspection ${inspectionEP.getShortName} is null")
    description
  }

  def test_no_lowercase_language_used(): Unit = {
    assert(!acquireAllInspectionEPs()
      .flatMap(insp => Option(insp.language))
      .exists(lang => lang != "Scala" && lang.toLowerCase == "scala"))
  }

  def test_all_inspections_have_descriptions(): Unit = {
    val inspectionsWithoutProperDescription =
      acquireAllScalaInspectionEPs().filter { inspectionEP =>
        val description = getDescription(inspectionEP)
        description == null ||
          description.length <= 5
      }.sortBy(_.getShortName)
        .map(insp => s"${insp.getShortName} (${insp.getDisplayName})")

    assert(inspectionsWithoutProperDescription.isEmpty,
      s"The following inspection do not have a description file:\n  ${inspectionsWithoutProperDescription.mkString(",\n  ")}")
  }

  def test_all_shortNames_are_unique(): Unit = {
    val allShortNames = acquireAllInspectionEPs().map(_.getShortName).groupBy(identity).view.mapValues(_.length)
    val scalaShortNames = acquireAllScalaInspectionEPs().map(_.getShortName)

    scalaShortNames.foreach { scalaShortName =>
      assert(allShortNames(scalaShortName) == 1, s"shortName $scalaShortName exists multiple times!")
    }
  }

  def test_all_inspection_descriptions_have_tooltip_end(): Unit = {
    val inspectionsWithoutProperDescription =
      acquireAllScalaInspectionEPs().filterNot { inspectionEP =>
        getDescription(inspectionEP).contains("<!-- tooltip end -->")
      }.sortBy(_.getShortName)
        .map(insp => s"${insp.getShortName} (${insp.getDisplayName})")

    assert(inspectionsWithoutProperDescription.isEmpty,
      s"The following inspection's description files don't have <!-- tooltip end -->:\n  ${inspectionsWithoutProperDescription.mkString(",\n  ")}")
  }

  def test_all_inspection_code_blocks_are_indented_by_at_least_two_space(): Unit = {
    val regex = raw"""<pre><code>((.|\n)*)</pre></code>""".r
    val inspectionsWithoutProperDescription =
      acquireAllScalaInspectionEPs().filterNot { inspectionEP =>
        regex.findAllMatchIn(getDescription(inspectionEP)).forall { aMatch =>
          val code = aMatch.group(1)
          code.linesIterator.forall(s => s.isBlank || s.startsWith("  "))
        }
      }.sortBy(_.getShortName)
        .map(insp => s"${insp.getShortName} (${insp.getDisplayName})")

    assert(inspectionsWithoutProperDescription.isEmpty,
      s"The following inspection's description files have code blocks with wrong indentation:\n  ${inspectionsWithoutProperDescription.mkString(",\n  ")}")
  }

  def test_run_all_inspections(): Unit = {
    myFixture.configureByText("foo.scala", "class A")
    val inspectionManager = InspectionManager.getInstance(getProject)

    val inspections = acquireAllScalaInspectionEPs()

    inspections.foreach { inspection =>
      val inspectionTool = inspection.instantiateTool()
      myFixture.enableInspections(inspectionTool)

      assertNoThrowable(() => {
        val highlights = myFixture.doHighlighting()
        assertFalse("Expecting at least 1 highlight", highlights.isEmpty)
      })

      myFixture.disableInspections(inspectionTool)

      inReadAction {
        val holder = new ProblemsHolder(inspectionManager, getFile, true)
        val fileRange = getFile.getTextRange

        InspectionEngine.withSession(getFile, fileRange, fileRange, null, true, session => {
          val visitor = inspectionTool.asInstanceOf[LocalInspectionTool].buildVisitor(holder, true, session)
          assertNoThrowable(() => {
            visitor.visitFile(getFile)
            visitor.visitElement(getFile)
          })
        })
      }
    }
  }
}
