package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

abstract class AbstractIntention(@Nls text: String, @Nls familyName: String)
                                (f: (Project, Editor) => PartialFunction[PsiElement, Unit])
  extends PsiElementBaseIntentionAction {

  override def getText: String = text

  override def getFamilyName: String = familyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    f(project, editor).isDefinedAt(element)

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    f.apply(project, editor)(element)
}
