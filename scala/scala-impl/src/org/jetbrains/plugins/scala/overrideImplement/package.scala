package org.jetbrains.plugins.scala

import com.intellij.codeInsight.generation.{ClassMemberWithElement, PsiElementClassMember}
import com.intellij.psi.PsiDocCommentOwner

package object overrideImplement {
  type ClassMember = PsiElementClassMember[_ <: PsiDocCommentOwner] with ScalaNamedMember
  type ClassMember0 = ClassMemberWithElement with ScalaMember
}
