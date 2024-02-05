package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHint, MouseHandler}
import org.jetbrains.plugins.scala.codeInsight.intention.expression.MakeImplicitConversionExplicit

import scala.jdk.CollectionConverters._

class MakeConversionExplicit extends AnAction(
  ScalaCodeInsightBundle.message("make.conversion.explicit.action.text"),
  ScalaCodeInsightBundle.message("make.conversion.explicit.action.description"),
  null
) {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = ImplicitHint.elementOf(inlay)

    new MakeImplicitConversionExplicit().invoke(e.getData(CommonDataKeys.PROJECT), editor, element)

    val prefixAndSuffixInlays =
      if (inlay == null) Seq()
      else {
        val startOffset = inlay.getOffset
        val endOffset = {
          val inlayText = inlay.getRenderer.asInstanceOf[HintRenderer].getText
          startOffset + inlayText.length + element.getTextLength + 1
        }

        model.getInlineElementsInRange(startOffset, endOffset).asScala
      }

    prefixAndSuffixInlays.foreach(_.dispose())
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
