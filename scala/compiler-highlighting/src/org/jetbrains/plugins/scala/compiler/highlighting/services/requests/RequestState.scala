package org.jetbrains.plugins.scala.compiler.highlighting.services.requests

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.project.Project

enum RequestState:
  case Ready, NotReady, Expired
  
object RequestState:
  /**
   * `Expired` when nothing can show the result, `NotReady` while the user is mid-gesture, `Ready`
   * otherwise.
   *
   * A document with no editor is expired rather than delayed: there is nowhere to draw the answer, so
   * waiting for one would mean waiting for ever. An active lookup or live template is the opposite case,
   * a gesture that usually ends without the text changing, so the work is worth keeping and retrying.
   */
  def of(project: Project, document: Document): RequestState =
    val editors = EditorFactory.getInstance().getEditors(document, project)
    if editors.isEmpty then RequestState.Expired
    else if editors.exists(usable(project, _)) then RequestState.Ready
    else RequestState.NotReady

  private def usable(project: Project, editor: com.intellij.openapi.editor.Editor): Boolean =
    LookupManager.getActiveLookup(editor) == null &&
      TemplateManager.getInstance(project).getActiveTemplate(editor) == null
  

