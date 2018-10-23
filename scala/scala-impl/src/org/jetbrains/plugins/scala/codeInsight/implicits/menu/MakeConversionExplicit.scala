package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.expression.MakeImplicitConversionExplicit

class MakeConversionExplicit(element: PsiElement) extends AnAction("Make conversion explicit") {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)

    val prefixAndSuffixInlays = {
      val model = editor.getInlayModel
      val offset = element.getTextOffset
      model.getInlineElementsInRange(offset, offset + element.getTextLength)
    }

    prefixAndSuffixInlays.forEach(_.dispose())

    new MakeImplicitConversionExplicit().invoke(e.getData(CommonDataKeys.PROJECT), editor, element)
  }
}
