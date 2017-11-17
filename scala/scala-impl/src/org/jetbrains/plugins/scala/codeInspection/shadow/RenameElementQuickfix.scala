package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.ide.DataManager
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.refactoring.actions.RenameElementAction
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions._

import scala.collection.{JavaConverters, mutable}

/**
 * User: Alefas
 * Date: 06.02.12
 */
class RenameElementQuickfix(myRef: PsiElement, name: String) extends AbstractFixOnPsiElement(name, myRef) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    val action: AnAction = new RenameElementAction
    val event: AnActionEvent = actionEventForElement(element, action)
    invokeLater {
      action.actionPerformed(event)
    }
  }

  private def actionEventForElement(ref: PsiElement, action: AnAction)
                                   (implicit project: Project): AnActionEvent = {
    val map = mutable.Map.empty[String, AnyRef]
    val containingFile = ref.getContainingFile
    val editor: Editor = InjectedLanguageUtil.openEditorFor(containingFile, project)
    if (editor.isInstanceOf[EditorWindow]) {
      map.put(CommonDataKeys.EDITOR.getName, editor)
      map.put(CommonDataKeys.PSI_ELEMENT.getName, ref)
    } else if (ApplicationManager.getApplication.isUnitTestMode) {
      val element = new TextEditorPsiDataProvider().getData(CommonDataKeys.PSI_ELEMENT.getName,
        editor, editor.getCaretModel.getCurrentCaret)
      map.put(CommonDataKeys.PSI_ELEMENT.getName, element)
    }

    import JavaConverters._
    val dataContext = SimpleDataContext.getSimpleContext(map.asJava, DataManager.getInstance.getDataContext(editor.getComponent))
    new AnActionEvent(null, dataContext, "", action.getTemplatePresentation, ActionManager.getInstance, 0)
  }
}
