package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScEnumerators

object ScEnumeratorsAnnotator extends ElementAnnotator[ScEnumerators] {

  override def annotate(enumerators: ScEnumerators, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    val msg = ScalaBundle.message("semicolon.not.allowed.here")
    enumerators.erroneousSemicolons.foreach { errSemicolon =>
      val annotation = holder.createErrorAnnotation(errSemicolon, msg)
      annotation.registerFix(new RemoveErrorSemicolonIntentionAction(enumerators))
    }
  }

  private class RemoveErrorSemicolonIntentionAction(enumerators: ScEnumerators) extends IntentionAction {

    override def getText: String = ScalaBundle.message("remove.all.erroneous.semicolons.from.forexpression")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
      enumerators.erroneousSemicolons.foreach(_.delete())

    override def startInWriteAction(): Boolean = true

    override def getFamilyName: String = "Remove all erroneous semicolons from for expression"
  }
}
