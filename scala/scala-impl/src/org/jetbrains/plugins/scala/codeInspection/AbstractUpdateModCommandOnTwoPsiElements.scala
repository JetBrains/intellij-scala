package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import com.intellij.psi.{PsiElement, SmartPointerManager}

abstract class AbstractUpdateModCommandOnTwoPsiElements[T <: PsiElement, U <: PsiElement](
  @IntentionFamilyName override val getFamilyName: String,
  startElement: T,
  endElement: U,
) extends PsiUpdateModCommandAction[T](startElement) {
  private val myEndElement = SmartPointerManager.createPointer(endElement)

  override final def invoke(context: ActionContext, element: T, updater: ModPsiUpdater): Unit = {
    val endElement = myEndElement.getElement
    if (endElement != null) {
      doInvoke(context, element, updater.getWritable(endElement), updater)
    }
  }

  protected def doInvoke(context: ActionContext, startElement: T, endElement: U, updater: ModPsiUpdater): Unit
}
