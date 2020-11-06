package org.jetbrains.plugins.scala
package highlighter
package usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.Markers

class ScalaHighlightConstructorInvocationUsagesTest extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers with Markers {
  val | = CARET
  val |< = startMarker
  val >| = endMarker

  def testClassDefinitionUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Te${|}st${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testClassConstructorInvocationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Te${|}st${>|}
         |  new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorUsage(): Unit = {
    val code =
      s"""
         |obejct Obj {
         |  class ${|<}Test${>|} {
         |    def ${|<}this${>|}(i: Int) = this()
         |  }
         |  val x: ${|<}Test${>|} = new ${|<}Te${|}st${>|}(3)
         |  new ${|<}Test${>|}
         |}
         |""".stripMargin
    doTest(code)
  }

  def testAuxiliaryConstructorInvoctionUsage(): Unit = {
    val code =
      s"""
         |obejct Obj {
         |  class Test {
         |    def ${|<}th${|}is${>|}(i: Int) = this()
         |  }
         |  val x: Test = new ${|<}Test${>|}(3)
         |  new Test
         |}
         |""".stripMargin
    doTest(code)
  }

  def testClassTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  class ${|<}Test${>|}
         |  val x: ${|<}Te${|}st${>|} = new ${|<}Test${>|}
         |  new ${|<}Test${>|}
         |}
       """.stripMargin
    doTest(code)
  }

  def testTraitTypeAnnotationUsage(): Unit = {
    val code =
      s"""
         |object Obj {
         |  trait ${|<}Test${>|}
         |  val x: ${|<}Test${>|} = new ${|<}Te${|}st${>|} {}
         |  new ${|<}Test${>|} {}
         |}
       """.stripMargin
    doTest(code)
  }

  def doTest(fileText: String): Unit = {
    val (fileTextWithoutMarkers, expectedRanges) = extractSequentialMarkers(fileText.withNormalizedSeparator, considerCaret = true)
    val file = myFixture.configureByText("dummy.scala", fileTextWithoutMarkers)
    val finalFileText = file.getText

    val editor = myFixture.getEditor
    HighlightUsagesHandler.invoke(myFixture.getProject, editor, file)

    val highlighters = editor.getMarkupModel.getAllHighlighters
    val actualRanges = highlighters.map(hr => TextRange.create(hr.getStartOffset, hr.getEndOffset)).toSeq

    val expected = rangeSeqToComparableString(expectedRanges, finalFileText)
    val actual = rangeSeqToComparableString(actualRanges, finalFileText)

    actual shouldBe expected
  }

  private def rangeSeqToComparableString(ranges: Seq[TextRange], fileText: String): String =
    ranges.sortBy(_.getStartOffset).map { range =>
      val start = range.getStartOffset
      val end = range.getEndOffset
      s"($start, $end): " + fileText.substring(start, end)
    }.mkString("\n")
}
