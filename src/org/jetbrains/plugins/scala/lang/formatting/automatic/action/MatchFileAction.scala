package org.jetbrains.plugins.scala
package lang.formatting.automatic.action

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, AnActionEvent, AnAction}
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDirectory, PsiFile, PsiDocumentManager}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.formatting.Indent
import com.intellij.psi.codeStyle.CodeStyleSettings

/**
 * Created by Roman.Shein on 14.07.2014.
 */
class MatchFileAction extends AnAction {
  override def actionPerformed(e: AnActionEvent) {
    println("match file action is performed")
    val dataContext: DataContext = e.getDataContext
    val project: Project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project == null) {
      return
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val editor: Editor = CommonDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
      if (file == null) return
      val astNode = file.getNode
      val codeStyleSettings = new CodeStyleSettings
      val topBlock = new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
      ScalaBlock.matchBlock(topBlock)
    }
  }

  override def update(e: AnActionEvent) {
    ScalaActionUtil enableAndShowIfInScalaFile e
  }
}