package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic


import java.util.{ArrayList, List}
import javax.swing.Icon

import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod
import org.jetbrains.plugins.scala.icons.Icons
/**
 * @author ilyas
 */

trait PsiMethodFake extends PsiMethod {
  def setName(name: String): PsiElement = null

  def hasModifierProperty(name: String): Boolean = false

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false

  def hasTypeParameters: Boolean = false

  def psiTypeParameters: Array[PsiTypeParameter] = null

  def getTypeParameterList: PsiTypeParameterList = null

  def getHierarchicalMethodSignature: HierarchicalMethodSignature = null

  def isVarArgs: Boolean = false

  override def getParameterList: PsiParameterList = null

  def getBody: PsiCodeBlock = null

  def isConstructor: Boolean = false

  def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def findDeepestSuperMethod: PsiMethod = null

  def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def getReturnTypeElement: PsiTypeElement = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List[MethodSignatureBackedByPsiMethod] =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY
}