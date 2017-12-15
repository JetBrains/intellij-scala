package scala.meta.annotations

import com.intellij.testFramework.TestActionEvent
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.junit.Assert._

abstract class MetaAnnotationUndoExpansionTestBase extends MetaAnnotationTestBase {

  protected def checkUndo(annotationText: String, testFileText: String): Unit = {
    compileMetaSource(mkAnnot(annotName, annotationText))
    val trimmed = normalize(testFileText)
    myFixture.configureByText(s"$testClassName.scala", trimmed)
    val expandGutter = getGutter
    expandGutter.getClickAction.actionPerformed(new TestActionEvent())
    val expandedText = normalize(myFixture.getEditor.getDocument.getText)
    assertNotEquals("annotation failed to expand", expandedText, trimmed)
    val collapseGutter = getGutter
    collapseGutter.getClickAction.actionPerformed(new TestActionEvent())
    val collapsedText = normalize(myFixture.getEditor.getDocument.getText)
    assertEquals("undo doesn't result in initial content", trimmed, collapsedText)
  }

}
