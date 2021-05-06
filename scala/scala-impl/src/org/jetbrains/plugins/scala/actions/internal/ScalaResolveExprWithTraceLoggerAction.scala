package org.jetbrains.plugins.scala.actions.internal

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.traceLogger.TraceLogger

class ScalaResolveExprWithTraceLoggerAction extends AnAction(
  "Resolve Expression with TraceLogger",
  "Resolve an expression and use the TraceLogger to log the resolving",
  /* icon = */ null
){
  override def update(e: AnActionEvent): Unit = ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def actionPerformed(e: AnActionEvent): Unit = {
    val context = e.getDataContext
    implicit val project: Project = CommonDataKeys.PROJECT.getData(context)
    implicit val editor: Editor = CommonDataKeys.EDITOR.getData(context)
    if (project == null || editor == null)
      return

    val file = PsiUtilBase.getPsiFileInEditor(editor, project)
    val reference = findSelectedReference(file)

    reference.foreach {
      reference =>
        TraceLogger.runWithTraceLogger(s"resolve-${reference.refName}") {
          TraceLogger.log(s"Log resolving of '${reference.getText}''")
          ScalaPsiManager.instance(project).clearAllCachesAndWait()
          TraceLogger.block("block test") {
            TraceLogger.log("some log msg")
          }
          TraceLogger.block("nothing in there") {

          }
          val results =
            reference.multiResolveScala(false) match {
              case Array() =>
                TraceLogger.log("Didn't find any resolve results, so try to resolve incomplete")
                reference.multiResolveScala(true)
              case results =>
                results
            }
          TraceLogger.log("Done.", results)
        }
    }
  }

  private def findSelectedReference(file: PsiFile)(implicit editor: Editor): Option[ScReference] = {
    val sm = editor.getSelectionModel
    val selection = TextRange.create(sm.getSelectionStart, sm.getSelectionEnd)
    file.findElementAt(selection.getStartOffset).toOption.flatMap(
      _.withParentsInFile
        .dropWhile(!_.getTextRange.contains(selection))
        .collectFirst { case ref: ScReference => ref }
    )
  }
}
