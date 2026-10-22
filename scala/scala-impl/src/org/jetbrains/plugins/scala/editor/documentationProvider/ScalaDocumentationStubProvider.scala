package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.DocumentationStubProvider
import com.intellij.psi.{PsiComment, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

final class ScalaDocumentationStubProvider extends DocumentationStubProvider {

  override def documentationStub(element: PsiElement): String = element match {
    case owner: ScDocCommentOwner if owner.getDocComment != null =>
      ScalaDocStubGenerator.createScalaDocStub(owner)
    case _ => null
  }

  override def findDocComment(element: PsiElement): PsiComment = element match {
    case owner: ScDocCommentOwner => owner.getDocComment
    case _ => null
  }

  override def findDocumentedElement(element: PsiElement): PsiElement =
    PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScDocCommentOwner])
}
