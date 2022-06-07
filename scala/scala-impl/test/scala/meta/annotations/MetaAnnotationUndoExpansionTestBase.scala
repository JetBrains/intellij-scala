package scala.meta.annotations

import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert._

abstract class MetaAnnotationUndoExpansionTestBase extends MetaAnnotationTestBase {

  protected def checkUndo(annotationText: String, testFileText: String): Unit = {
    compileMetaSource(mkAnnot(annotName, annotationText))
    val trimmed = testFileText.withNormalizedSeparator.trim
    myFixture.configureByText(s"$testClassName.scala", trimmed)
    val expandGutter = getGutter
    expandGutter.getClickAction.actionPerformed(new TestActionEvent())
    val expandedText = myFixture.getEditor.getDocument.getText.withNormalizedSeparator.trim
    assertNotEquals("annotation failed to expand", expandedText, trimmed)
    val collapseGutter = getGutter
    collapseGutter.getClickAction.actionPerformed(new TestActionEvent())
    val collapsedText = myFixture.getEditor.getDocument.getText.withNormalizedSeparator.trim
    assertEquals("undo doesn't result in initial content", trimmed, collapsedText)
  }

}
