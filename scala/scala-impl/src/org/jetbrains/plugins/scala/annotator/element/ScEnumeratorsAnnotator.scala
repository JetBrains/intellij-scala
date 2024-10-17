package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScEnumerators

object ScEnumeratorsAnnotator extends ElementAnnotator[ScEnumerators] with DumbAware {

  override def annotate(enumerators: ScEnumerators, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val msg = ScalaBundle.message("semicolon.not.allowed.here")
    findErroneousSemicolons(enumerators).foreach { errSemicolon =>
      holder.createErrorAnnotation(errSemicolon, msg, new RemoveErrorSemicolonIntentionAction(enumerators))
    }
  }

  private final class RemoveErrorSemicolonIntentionAction(enumerators: ScEnumerators)
    extends IntentionAction
      with DumbAware {
    override def getText: String = ScalaBundle.message("remove.all.erroneous.semicolons.from.forexpression")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
      findErroneousSemicolons(enumerators).foreach(_.delete())

    override def startInWriteAction(): Boolean = true

    override def getFamilyName: String = ScalaBundle.message("remove.all.erroneous.semicolons.from.forexpression")

    override def getFileModifierForPreview(target: PsiFile): FileModifier =
      new RemoveErrorSemicolonIntentionAction(PsiTreeUtil.findSameElementInCopy(enumerators, target))
  }

  private def findErroneousSemicolons(enumerators: ScEnumerators): Seq[PsiElement] = {
    val allChildren = enumerators.children.filter(!_.is[PsiWhiteSpace]).toSeq
    if (allChildren.isEmpty)
      return Seq.empty

    var lastInitialSemicolon = Option.empty[PsiElement]
    val errSemiBuilder = Seq.newBuilder[PsiElement]
    var canHaveSemicolon = false
    allChildren.foreach {
      case Whitespace(_) =>
      case semi@ElementType(ScalaTokenTypes.tSEMICOLON) =>
        if (canHaveSemicolon) lastInitialSemicolon = Some(semi)
        else errSemiBuilder += semi

        canHaveSemicolon = false
      case _ =>
        lastInitialSemicolon = None
        canHaveSemicolon = true
    }
    errSemiBuilder ++= lastInitialSemicolon
    errSemiBuilder.result()
  }
}
