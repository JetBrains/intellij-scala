package org.jetbrains.plugins.scala.annotator.intention

import lang.psi._
import lang.psi.api.ScalaFile
import lang.resolve.{ResolveUtils}
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.util.FQNameCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope

import lang.psi.api.base.ScReferenceElement
import _root_.scala.collection.mutable._

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object OuterImportsActionCreator {
  def getOuterImportFixes(refElement: ScReferenceElement, project: Project): Seq[IntentionAction] = {
    val actionList = new ListBuffer[IntentionAction]

    val facade = JavaPsiFacade.getInstance(project)
    val classes = facade.getShortNamesCache().getClassesByName(refElement.refName, GlobalSearchScope.allScope(project))

    val kinds = refElement.getKinds(false)
    for (clazz <- classes) {
      val qName = clazz.getQualifiedName()
      if (qName != null && qName.indexOf('.') != -1 && ResolveUtils.kindMatches(clazz, kinds)) {
        val action: IntentionAction = new IntentionAction() {
          def getText = ScalaBundle.message("import.with", Array[Object](qName))
          def getFamilyName = ScalaBundle.message("import.class")
          def isAvailable(project: Project, editor: Editor, file: PsiFile) = refElement.isValid
          def invoke(project: Project, editor: Editor, file: PsiFile) = file match {
            case x: ScalaFile => {
              if (QuickfixUtil.ensureFileWritable(project, file)) {
                //TODO[sasha] this is not entirely correct, sometimes adding imports does not cause the ref to resolve to the class,
                //so a correct way would be to call bindToElement which in turn will addImportForClass
                //falling over to inserting qualified ref (probably even with _root_ as a prefix).
                x.addImportForClass(clazz)
              }
            }
          }
          def startInWriteAction(): Boolean = true
        }
        actionList += action
      }
    }
    return actionList.toSeq
  }
}