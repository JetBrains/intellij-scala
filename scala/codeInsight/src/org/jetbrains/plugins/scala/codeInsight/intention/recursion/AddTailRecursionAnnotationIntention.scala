package org.jetbrains.plugins.scala
package codeInsight
package intention
package recursion

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.FunctionAnnotator
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements._

final class AddTailRecursionAnnotationIntention extends PsiElementBaseIntentionAction {

  import AddTailRecursionAnnotationIntention._

  override def isAvailable(project: Project,
                           editor: Editor,
                           element: PsiElement): Boolean =
    CanBeTailRecursive.unapply(element).isDefined

  override def invoke(project: Project,
                      editor: Editor,
                      element: PsiElement): Unit =
    element.getParent match {
      case function: ScFunctionDefinition => addTailRecursionAnnotation(function)
    }

  override def getText: String = ScalaCodeInsightBundle.message("no.tailrec.annotation.fix")

  override def getFamilyName = ScalaCodeInsightBundle.message("family.name.recursion")
}

object AddTailRecursionAnnotationIntention {

  import FunctionAnnotator._

  object CanBeTailRecursive {

    def unapply(element: PsiElement): Option[ScFunctionDefinition] = element.getParent match {
      case function: ScFunctionDefinition if function.nameId == element &&
        findTailRecursionAnnotation(function).isEmpty &&
        function.recursiveReferencesGrouped.tailRecursionOnly => Some(function)
      case _ => None
    }
  }

  def addTailRecursionAnnotation(function: ScFunctionDefinition): Unit = {
    function.addAnnotation(TailrecAnnotationFQN)
  }
}