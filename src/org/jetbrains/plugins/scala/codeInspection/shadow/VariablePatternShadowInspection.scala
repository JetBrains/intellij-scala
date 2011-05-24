package org.jetbrains.plugins.scala
package codeInspection
package shadow

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import lang.psi.impl.ScalaPsiElementFactory
import extensions._
import lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.ide.DataManager
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.openapi.actionSystem._
import lang.psi.ScalaPsiUtil

class VariablePatternShadowInspection extends AbstractInspection("VariablePatternShadow", "Suspicious shadowing by a Variable Pattern") {
  val description: String = """Detects a Variable Pattern that shadows a stable identifier defined in the enclosing scope.
  To perform an equality test against that value, use backticks, e.g. <code>val foo = 0; 0 match {case `foo` => }</code>
  """

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case refPat: ScReferencePattern => check(refPat, holder)
  }

  private def check(refPat: ScReferencePattern, holder: ProblemsHolder) {
    val isInCaseClause = ScalaPsiUtil.nameContext(refPat).isInstanceOf[ScCaseClause]
    if (isInCaseClause) {
      val dummyRef = ScalaPsiElementFactory.createReferenceFromText(refPat.name, refPat.getContext.getContext, refPat)
      val resolve = dummyRef.resolve()
      if (resolve != null) {
        holder.registerProblem(refPat.nameId, getDisplayName, new ConvertToStableIdentifierPatternFix(refPat), new RenameVariablePatternFix(refPat))
      }
    }
  }
}

class ConvertToStableIdentifierPatternFix(ref: ScReferencePattern)
        extends AbstractFix("Convert to Stable Identifier Pattern `%s`".format(ref.getText), ref) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    val stableIdPattern = ScalaPsiElementFactory.createPatternFromText("`%s`".format(ref.getText), ref.getManager)
    ref.replace(stableIdPattern)
  }
}

class RenameVariablePatternFix(ref: ScReferencePattern) extends AbstractFix("Rename Variable Pattern", ref) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!ref.isValid) return
    val action: AnAction = new RenameElementAction
    val event: AnActionEvent = actionEventForElement(descriptor, project, action)
    invokeLater {
      action.actionPerformed(event)
    }
  }

  private def actionEventForElement(descriptor: ProblemDescriptor, project: Project, action: AnAction): AnActionEvent = {
    import collection.JavaConversions._
    import collection.mutable

    val map = mutable.Map[String, AnyRef]()
    val psiElement = descriptor.getPsiElement
    val containingFile = ref.getContainingFile
    val editor: Editor = InjectedLanguageUtil.openEditorFor(containingFile, project)
    if (editor.isInstanceOf[EditorWindow]) {
      map.put(PlatformDataKeys.EDITOR.getName, editor)
      map.put(LangDataKeys.PSI_ELEMENT.getName, ref)
    } else if (ApplicationManager.getApplication.isUnitTestMode) {
      val element = new TextEditorPsiDataProvider().getData(LangDataKeys.PSI_ELEMENT.getName,
        editor, containingFile.getVirtualFile)
      map.put(LangDataKeys.PSI_ELEMENT.getName, element)
    }
    val dataContext = SimpleDataContext.getSimpleContext(map, DataManager.getInstance.getDataContext)
    new AnActionEvent(null, dataContext, "", action.getTemplatePresentation, ActionManager.getInstance, 0)
  }
}