package org.jetbrains.plugins.scala

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.psi.PsiDocCommentOwner

package object overrideImplement {
  type ClassMember = PsiElementClassMember[_ <: PsiDocCommentOwner] with ScalaNamedMember

}
