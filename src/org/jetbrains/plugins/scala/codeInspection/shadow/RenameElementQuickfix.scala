package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ProblemDescriptor
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
import org.jetbrains.plugins.scala.codeInspection.AbstractFix
import org.jetbrains.plugins.scala.extensions._

/**
 * User: Alefas
 * Date: 06.02.12
 */

class RenameElementQuickfix(ref: PsiElement, name: String) extends AbstractFix(name, ref) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!ref.isValid) return
    val action: AnAction = new RenameElementAction
    val event: AnActionEvent = actionEventForElement(descriptor, project, action)
    invokeLater {
      action.actionPerformed(event)
    }
  }

  private def actionEventForElement(descriptor: ProblemDescriptor, project: Project, action: AnAction): AnActionEvent = {
    import scala.collection.JavaConversions._
    import scala.collection.mutable

    val map = mutable.Map[String, AnyRef]()
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
    val dataContext = SimpleDataContext.getSimpleContext(map, DataManager.getInstance.getDataContext)
    new AnActionEvent(null, dataContext, "", action.getTemplatePresentation, ActionManager.getInstance, 0)
  }
}
