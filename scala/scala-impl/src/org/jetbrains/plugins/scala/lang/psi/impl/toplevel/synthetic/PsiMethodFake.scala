package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic


import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.plugins.scala.icons.Icons

import java.util.{ArrayList, List}
import javax.swing.Icon
/**
 * @author ilyas
 */

trait PsiMethodFake extends PsiMethod {
  override def setName(name: String): PsiElement = null

  override def hasModifierProperty(name: String): Boolean = false

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  override def getDocComment: PsiDocComment = null

  override def isDeprecated: Boolean = false

  override def hasTypeParameters: Boolean = false

  def psiTypeParameters: Array[PsiTypeParameter] = null

  override def getTypeParameterList: PsiTypeParameterList = null

  override def getHierarchicalMethodSignature: HierarchicalMethodSignature = null

  override def isVarArgs: Boolean = false

  override def getParameterList: PsiParameterList = null

  override def getBody: PsiCodeBlock = null

  override def isConstructor: Boolean = false

  override def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findDeepestSuperMethod: PsiMethod = null

  override def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def getReturnTypeElement: PsiTypeElement = null

  override def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List[MethodSignatureBackedByPsiMethod] =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  override def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY
}