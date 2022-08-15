package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions.{&&, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

final class ScalaBlankLineContextType
  extends ScalaFileTemplateContextType.ElementContextType(ScalaCodeInsightBundle.message("element.context.type.blank.line")) {

  override protected def isInContext(offset: Int)
                                    (implicit file: ScalaFile): Boolean = {
    val element = file.findElementAt(offset)
    element match {
      case (_: LeafPsiElement) && Parent(ref: ScReference) => // some prefix of `apply`
        ref.startsFromNewLine(false) && ref.followedByNewLine(false)
      case ws: PsiWhiteSpace =>
        // this check can be not enough when a user tries to search for some template
        // without typing at least 1 it's prefix char, for example:
        // object O {
        //  "some string".   $CARET
        // }
        // But we assume that overwhelming majority will type some prefix of a template
        ws.textContains('\n') && ws.startOffset < offset
      case _ =>
        false
    }
  }
}
