package org.jetbrains.plugins.scala.editor.todo

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType}
import com.intellij.ide.todo.TodoIndexPatternProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.{Segment, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.TodoCacheManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.Markers
import org.junit.Assert._

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.math.Ordering.comparatorToOrdering

/** see analogue class from platform: [[com.intellij.editor.TodoItemsTestCase]] */
abstract class ScalaTodoItemsTestBase extends ScalaLightCodeInsightFixtureTestAdapter with Markers {

  protected val start = startMarker
  protected val end = endMarker
  protected def start(i: Int = 0) = startMarker(i)
  protected def end (i: Int = 0) = endMarker(i)

  protected def testTodos(textWithMarkers: String)
                         (implicit fileType: FileType = ScalaFileType.INSTANCE): Unit = {
    val (text, ranges) = extractSequentialMarkers(textWithMarkers.withNormalizedSeparator)

    val file = configureFromFileText(text, fileType)

    EditorTestUtil.setEditorVisibleSize(getEditor, 1000, 1000) // set visible area for highlighting

    val expectedTodoRanges = ranges
    val actualTodoRanges = extractActualTodoRanges
    assertEquals(expectedTodoRanges, actualTodoRanges)

    val vFile = file.getVirtualFile
    assertNotNull(vFile)
    assertSameTodoCountInIndexAndHighlighting(vFile)
  }

  private def extractActualTodoRanges: Seq[TextRange] = {
    val highlightInfos = myFixture.doHighlighting().asScala.toSeq
    findActualTodoRanges(highlightInfos)
  }

  private def findActualTodoRanges(highlightInfos: Seq[HighlightInfo]): Seq[TextRange] =
    highlightInfos
      .filter(_.`type` == HighlightInfoType.TODO)
      .map(info => TextRange.create(info.getHighlighter))
      .sorted(comparatorToOrdering(Segment.BY_START_OFFSET_THEN_END_OFFSET))

  private def assertSameTodoCountInIndexAndHighlighting(vFile: VirtualFile): Unit = {
    val todosInIndex = TodoCacheManager.SERVICE.getInstance(getProject).getTodoCount(vFile, TodoIndexPatternProvider.getInstance)
    val todosInHighlighting = PsiTodoSearchHelper.SERVICE.getInstance(getProject).findTodoItems(getFile).length
    assertEquals("Mismatch between todos in index and highlighting", todosInIndex, todosInHighlighting)
  }
}