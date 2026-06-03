package org.jetbrains.plugins.scala.incremental

import com.intellij.codeInspection.{InspectionProfileEntry, LocalInspectionEP}
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.Tracing.{Parameters, tracing}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.junit.Assert

class HighlightingTest extends ScalaFixtureTestCase {
  @Language("Scala")
  private final val Code =
    """
      |import scala.io.Source
      |import scala.collection.immutable.Set
      |
      |class Foo extends Runnable with Serializable {
      |  /**
      |   * @return Unit
      |   * @see [[java.lang.Runnable]]
      |   */
      |  override def run(): Unit = {
      |    Seq(1, 2, 3).foreach(println)
      |  }
      |}
      |
      |object Foo {
      |  // Comment
      |  private val Bar: String = s"|foo${Foo.toString}bar".stripMargin
      |
      |  Source.fromString(Bar).toSeq.filter(_ > 1).headOption.isEmpty
      |}
      |""".stripMargin

  private val TypeInference =
    Parameters(resolve = true, inference = true, equivalence = true, conformance = true)

  def testOutsideVisibleArea(): Unit = {
    val psiFile = myFixture.configureByText("Foo.scala", Code)

    enableDefaultInspectionsIn(myFixture)

    projectSettings.setIncrementalHighlighting(true)
    setVisibleRangeIn(psiFile, TextRange.create(0, 1)) // Include the first, empty line

    val trace = tracing(myFixture.getProject, TypeInference)(myFixture.doHighlighting())
    Assert.assertEquals("", trace)
  }

  def testInsideVisibleArea(): Unit = {
    val psiFile = myFixture.configureByText("Foo.scala", "println()")

    projectSettings.setIncrementalHighlighting(true)
    setVisibleRangeIn(psiFile, TextRange.create(0, 9))

    val trace = tracing(myFixture.getProject, TypeInference)(myFixture.doHighlighting())
    Assert.assertTrue(trace.contains("scala.Predef.println"))
  }

  def testDisableInspections(): Unit = {
    val psiFile = myFixture.configureByText("Foo.scala", Code)

    enableDefaultInspectionsIn(myFixture)

    projectSettings.setCompilerHighlightingScala2(true)
    projectSettings.setCompilerHighlightingScala3(true)
    projectSettings.setDisableInspections(true)

    val trace = tracing(myFixture.getProject, TypeInference)(myFixture.doHighlighting())
    Assert.assertEquals("", trace)
  }

  private def projectSettings: ScalaProjectSettings =
    ScalaProjectSettings.getInstance(getProject)

  private def enableDefaultInspectionsIn(fixture: CodeInsightTestFixture): Unit = {
    val defaultInspections: Seq[InspectionProfileEntry] =
      LocalInspectionEP.LOCAL_INSPECTION.getExtensions().toSeq
        .filter(_.language == "Scala")
        .filter(_.enabledByDefault)
        .map(_.getInstance)

    myFixture.enableInspections(defaultInspections: _*)
  }

  private def setVisibleRangeIn(psiFile: PsiFile, range: TextRange): Unit = {
    val editor = {
      val document = PsiDocumentManager.getInstance(getProject).getDocument(psiFile)
      EditorFactory.getInstance.getEditors(document).head
    }
    editor.putUserData(VisibleRange.VISIBLE_RANGE_KEY, range)
    editor.putUserData(VisibleRange.EXACT_VISIBLE_RANGE_KEY, range)
  }
}
