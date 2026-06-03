package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

private object GotoOriginalHandlerUtil {
  private val WorksheetGotoPsiKey = new Key[PsiElement]("WORKSHEET_GOTO_PSI_KEY")

  def createPsi[I <: PsiElement, O <: PsiElement](creator: I => O, original: I): O = {
    val psi = creator(original)
    psi.putCopyableUserData(WorksheetGotoPsiKey, original)
    psi
  }

  def setGoToTarget(created: PsiElement, original: PsiElement): Unit = {
    created.putCopyableUserData(WorksheetGotoPsiKey, original)
  }

  def setGoToTarget2(created: PsiElement, original: PsiElement): Unit = {
    created.putUserData(WorksheetGotoPsiKey, original)
  }

  def getGoToTarget(created: PsiElement): Option[PsiElement] =
    Option(created.getCopyableUserData(WorksheetGotoPsiKey)).filter(_.isValid)

  def getGoToTarget2(created: PsiElement): Option[PsiElement] =
    Option(created.getUserData(WorksheetGotoPsiKey)).filter(_.isValid)
}
