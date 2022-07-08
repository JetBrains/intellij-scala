package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

private object GotoOriginalHandlerUtil {
  private val MY_KEY = new Key[PsiElement]("GOTO_ORIGINAL_HANDLER_BASE")

  def createPsi[I <: PsiElement, O <: PsiElement](creator: I => O, original: I): O = {
    val psi = creator(original)
    psi.putCopyableUserData(MY_KEY, original)
    psi
  }

  def storePsi(created: PsiElement, original: PsiElement): Unit = {
    created.putCopyableUserData(MY_KEY, original)
  }

  def storeNonModifiablePsi(created: PsiElement, original: PsiElement): Unit = {
    created.putUserData(MY_KEY, original)
  }

  def findPsi(created: PsiElement): Option[PsiElement] = Option(created.getCopyableUserData(MY_KEY)).filter(_.isValid)
}
