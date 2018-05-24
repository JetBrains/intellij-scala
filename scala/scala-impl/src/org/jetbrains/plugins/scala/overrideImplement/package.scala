package org.jetbrains.plugins.scala

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.psi.PsiDocCommentOwner

/**
 * Nikolay.Tropin
 * 2014-03-25
 */
package object overrideImplement {
  type ClassMember = PsiElementClassMember[_ <: PsiDocCommentOwner] with ScalaNamedMember

}
