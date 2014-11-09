package org.jetbrains.plugins.scala
package annotator
package quickfix.modifiers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner


/**
 * User: Alexander Podkhalyuzin
 * Date: 23.10.2008
 */

class AddModifierQuickFix(method: ScModifierListOwner, modifier: String) extends IntentionAction {
  def getText: String = ScalaBundle.message("add.modifier.fix", modifier)

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = method.isValid && method.getManager.isInProject(file)

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = method.setModifierProperty(modifier, true)

  def getFamilyName: String = ScalaBundle.message("add.modifier.fix", modifier)
}