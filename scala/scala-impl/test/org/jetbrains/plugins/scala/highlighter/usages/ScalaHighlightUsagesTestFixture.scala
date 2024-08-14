package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertEquals

final class ScalaHighlightUsagesTestFixture(
  fixture: CodeInsightTestFixture,
  startMarker: String = EditorTestUtil.SELECTION_START_TAG,
  endMarker: String = EditorTestUtil.SELECTION_END_TAG,
  fileType: FileType = ScalaFileType.INSTANCE,
) {

  var fixedFileName: Option[String] = None

  def doTest(fileTextWithMarkers: String): Unit = {
    val (fileTextWithoutMarkers, expectedRanges) = MarkersUtils.extractMarker(
      fileTextWithMarkers,
      caretMarker = Some(EditorTestUtil.CARET_TAG),
      startMarker = startMarker,
      endMarker = endMarker,
    )

    val fileName = fixedFileName.getOrElse(s"${fileTextWithMarkers.hashCode}.${fileType.getDefaultExtension}")
    val file = fixture.configureByText(fileName, fileTextWithoutMarkers)
    val fileTextFinal = file.getText //doesn't have caret tag

    val editor = fixture.getEditor

    // NOTE: A single virtual file can correspond to multiple PsiFiles
    // We need to get the one, which corresponds to the current caret position.
    // This is also done under the hood by the platform when highlighting usages.
    //
    // Example - Play/Twirl templates (with .scala.html extension).
    // Such files have 3 psi trees: Play2TemplatePsiFile, Play2ScalaFile, HtmlFileImpl.
    // If the caret is located inside scala code, we need to choose Play2ScalaFile.
    // Tip: you can see these 3 psi trees by invoking `fixture.getFile.getViewProvider.getAllFiles`)
    val actualFile = PsiUtilBase.getPsiFileInEditor(editor, fixture.getProject) // 49
    HighlightUsagesHandler.invoke(fixture.getProject, editor, actualFile)

    val highlighters = editor.getMarkupModel.getAllHighlighters
    val actualRanges = highlighters.map(hr => TextRange.create(hr.getStartOffset, hr.getEndOffset)).toSeq

    val expected = rangeSeqToComparableString(expectedRanges, fileTextFinal)
    val actual = rangeSeqToComparableString(actualRanges, fileTextFinal)

    assertEquals(expected, actual)
  }

  private def rangeSeqToComparableString(ranges: Seq[TextRange], fileText: String): String =
    ranges.sortBy(_.getStartOffset).map { range =>
      val start = range.getStartOffset
      val end = range.getEndOffset
      s"($start, $end): " + fileText.substring(start, end)
    }.mkString("\n")

  def multiCaret(caretIndex: Int): String = s"/*multiCaret$caretIndex*/"

  private val CaretPattern = """/\*multiCaret\d+\*/""".r

  def doTestWithDifferentCarets(fileTextWithMarkers: String): Unit = {
    val caretTags: Iterator[String] = Iterator.from(0).map(multiCaret)
    val caretTagsPresentInText = caretTags.takeWhile(fileTextWithMarkers.contains).toSeq

    //replace each caret tag with the main caret tag and remove the remaining tags
    val fileTextsWithSingleCaret: Seq[String] =
      caretTagsPresentInText
        .map(fileTextWithMarkers.replace(_, EditorTestUtil.CARET_TAG))
        .map(CaretPattern.replaceAllIn(_, ""))
        .toSeq

    class InnerException(i: Int, e: Throwable) extends Exception(s"Test failed on caret $i", e)

    for ((fileText, idx) <- fileTextsWithSingleCaret.zipWithIndex) {
      try {
        // We need to close the existing editor.
        // It is important when we use `fixedFileName`.
        // In this case the same file will be reused between `doTest` runs.
        // If we don't close the previous editor, there will be issues with file re-highlighting and usages won't be detected
        if (fixture.getFile != null) {
          FileEditorManager.getInstance(fixture.getProject).closeFile(fixture.getFile.getVirtualFile)
        }

        doTest(fileText)
      } catch {
        case e: Throwable =>
          throw new InnerException(idx, e)
      }
    }
  }
}
