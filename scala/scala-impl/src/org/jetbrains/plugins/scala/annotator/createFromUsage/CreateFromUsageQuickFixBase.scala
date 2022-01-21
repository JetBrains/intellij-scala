package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
abstract class CreateFromUsageQuickFixBase(ref: ScReference)
  extends IntentionAction with ProjectContextOwner {

  override implicit val projectContext: ProjectContext = ref

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!ref.isValid) return false

    val scalaFile = file match {
      case sf: ScalaFile => sf
      case _ => return false
    }
    if (!ScalaPsiManager.isInProjectOrStrachFile(scalaFile)) return false
    if (scalaFile.is[ScalaCodeFragment]) return false
    
    true
  }

  override def startInWriteAction() = false

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    Stats.trigger(FeatureKey.createFromUsage)
    invokeInner(project, editor, file)
  }

  protected def invokeInner(project: Project, editor: Editor, file: PsiFile): Unit
}