package org.jetbrains.plugins.scala.annotator.intention

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
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi._

import _root_.scala.collection.mutable._

/** 
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object OuterImportsActionCreator {
  def getOuterImportFixes(refElement: ScStableCodeReferenceElement, project: Project): Seq[IntentionAction] = {
    val actionList = new ListBuffer[IntentionAction]

    val facade = JavaPsiFacade.getInstance(project)
    val classes = facade.getShortNamesCache().getClassesByName(refElement.refName, GlobalSearchScope.allScope(project))


    for (clazz <- classes) {
      val qName = clazz.getQualifiedName()
      if (qName != null && qName.indexOf('.') != -1) {
        val action: IntentionAction = new IntentionAction() {
          def getText = ScalaBundle.message("import.with", Array[Object](qName))
          def getFamilyName = ScalaBundle.message("import.class", Array[Object]())
          def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
            return true
          }
          def invoke(project: Project, editor: Editor, file: PsiFile) {
            file match {
              case x: ScalaFile => {
                if (QuickfixUtil.ensureFileWritable(project, file)) {

                  x.addImportForClass(clazz)
                }
              }
              case _ =>
            }
          }
          def startInWriteAction(): Boolean = {
            return true;
          }
        }
        actionList += action
      }
    }
    return actionList.toSeq
  }
}