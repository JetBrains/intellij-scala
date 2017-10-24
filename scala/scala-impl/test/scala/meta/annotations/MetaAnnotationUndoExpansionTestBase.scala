package scala.meta.annotations

import com.intellij.testFramework.TestActionEvent
import org.junit.Assert._

abstract class MetaAnnotationUndoExpansionTestBase extends MetaAnnotationTestBase {

  import MetaAnnotationTestBase._

  protected def checkUndo(annotationText: String, testFileText: String): Unit = {
    compileMetaSource(mkAnnot(annotName, annotationText))
    val trimmed = testFileText.trim
    myFixture.configureByText(s"$testClassName.scala", trimmed)
    val expandGutter = getGutter
    expandGutter.getClickAction.actionPerformed(new TestActionEvent())
    assertNotEquals("annotation failed to expand", myFixture.getEditor.getDocument.getText.trim, trimmed)
    val collapseGutter = getGutter
    collapseGutter.getClickAction.actionPerformed(new TestActionEvent())
    assertEquals("undo doesn't result in initial content", trimmed, myFixture.getEditor.getDocument.getText.trim)
  }

}
