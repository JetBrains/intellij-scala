package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiClass

/**
 * Pavel Fatin
 */

object ClassQualifiedName {

  def unapply(clazz: PsiClass): Option[String] = clazz match {
    case null => None
    case _ => Option(clazz.qualifiedName)
  }
}