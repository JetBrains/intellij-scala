package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.PsiElementClassMember
import com.intellij.psi.PsiMethod
import lang.psi.types.PhysicalSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.09.2008
 */

class ScMethodMember(val sign: PhysicalSignature) extends PsiElementClassMember[PsiMethod](sign.method, sign.method.getName)