package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.implicits.{Hint, MouseHandler}
import org.jetbrains.plugins.scala.codeInsight.intention.expression.MakeImplicitConversionExplicit

class MakeConversionExplicit extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = Hint.elementOf(inlay)

    new MakeImplicitConversionExplicit().invoke(e.getData(CommonDataKeys.PROJECT), editor, element)

    val prefixAndSuffixInlays = {
      val startOffset = inlay.getOffset
      val endOffset = {
        val inlayText = inlay.getRenderer.asInstanceOf[HintRenderer].getText
        startOffset + inlayText.length + element.getTextLength + 1
      }

      model.getInlineElementsInRange(startOffset, endOffset)
    }

    prefixAndSuffixInlays.forEach(_.dispose())
  }
}
