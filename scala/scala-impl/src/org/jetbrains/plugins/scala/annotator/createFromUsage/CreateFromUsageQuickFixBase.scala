package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
abstract class CreateFromUsageQuickFixBase(ref: ScReferenceElement, description: String)
  extends IntentionAction with ProjectContextOwner {

  implicit val projectContext: ProjectContext = ref
  
  val getText = s"Create $description '${ref.nameId.getText}'"

  val getFamilyName = s"Create $description"

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!ref.isValid) return false
    if (file == null || !file.isInstanceOf[ScalaFile]) return false
    if (!ref.getManager.isInProject(file) && !file.asInstanceOf[ScalaFile].isWorksheetFile) return false
    if (file.isInstanceOf[ScalaCodeFragment]) return false
    
    true
  }

  override def startInWriteAction() = false

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    Stats.trigger(FeatureKey.createFromUsage)
    invokeInner(project, editor, file)
  }

  protected def invokeInner(project: Project, editor: Editor, file: PsiFile)
}