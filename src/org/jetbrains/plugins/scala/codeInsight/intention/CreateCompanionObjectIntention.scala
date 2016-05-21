package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * mattfowler
  * 5/21/2016
  */
class CreateCompanionObjectIntention extends PsiElementBaseIntentionAction {
  override def getText = "Create companion object for class"

  override def invoke(project: Project, editor: Editor, psiElement: PsiElement): Unit = {
    getClassIfAvailable(psiElement).foreach { clazz =>
      val companion = ScalaPsiElementFactory.createObjectWithContext(
        s"""|
            |object ${clazz.name} {
            |
            |}""".stripMargin, psiElement.getContext, psiElement)
      clazz.getParent.addAfter(companion, psiElement.getParent)
    }
  }

  override def isAvailable(project: Project, editor: Editor, psiElement: PsiElement): Boolean = {
    getClassIfAvailable(psiElement).exists(clazz => clazz.getParent.getChildren.forall {
      case obj: ScObject => obj.name != clazz.name
      case _ => true
    })
  }

  private def getClassIfAvailable(psiElement: PsiElement): Option[ScClass] = {
    psiElement match {
      case Parent(clazz: ScClass) => Some(clazz)
      case _ => None
    }
  }

  override def getFamilyName: String = CreateCompanionObjectIntention.getFamilyName
}


object CreateCompanionObjectIntention {
  def getFamilyName: String = "Create companion object"
}