package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.psi.PsiSubstitutor
import lang.psi.api.statements.ScTypeAlias
import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.ClassMember

/**
* User: Alexander Podkhalyuzin
* Date: 11.07.2008
*/

class ScAliasMember(member: ScTypeAlias) extends PsiElementClassMember[ScTypeAlias](member, member.name) {
  
}