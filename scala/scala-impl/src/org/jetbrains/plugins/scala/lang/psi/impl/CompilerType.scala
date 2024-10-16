package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

object CompilerType {
  private val TypeKey: Key[String] = Key.create("SCALA_COMPILER_TYPE_KEY")

  def apply(e: PsiElement): Option[String] =
    Option(e.getCopyableUserData(TypeKey))

  def update(e: PsiElement, tpe: Option[String]): Unit = {
    e.putCopyableUserData(TypeKey, tpe.orNull)
  }
}
