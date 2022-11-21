package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import scala.annotation.tailrec

final class ScalaImplicitValueClassContextType
  extends ScalaFileTemplateContextType.ElementContextType(ScalaCodeInsightBundle.message("element.context.type.implicit.value.class")) {

  override protected def isInContext(offset: Int)
                                    (implicit file: ScalaFile): Boolean = {
    val element = file.findElementAt(offset) match {
      case (_: LeafPsiElement) & Parent(ref: ScReference) => ref // expected to be an injected synthetic reference (for completion)
      case el                                             => el
    }

    val isTopLevel = element == null || isTopLevelElement(element)
    if (isTopLevel) {
      file.isWorksheetFile
    } else {
      isStaticallyAccessible(element)
    }
  }

  private def isTopLevelElement(element: PsiElement): Boolean =
    element.getParent.is[ScalaFile]

  @tailrec
  private def isStaticallyAccessible(element: PsiElement): Boolean =
    element.getParent match {
      case _: ScalaFile | _: ScPackaging                                             => true
      case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(obj: ScObject)) => isStaticallyAccessible(obj)
      case _                                                                         => false
    }
}
