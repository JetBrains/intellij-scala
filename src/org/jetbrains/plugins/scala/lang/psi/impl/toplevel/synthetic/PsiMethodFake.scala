package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic


import api.expr.ScNewTemplateDefinition
import com.intellij.openapi.editor.colors.TextAttributesKey
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Key
import icons.Icons
import com.intellij.psi.javadoc.PsiDocComment
import java.util.{ArrayList, List}
import javax.swing.Icon
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi._
import util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import java.lang.String
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

  def getTypeParameters: Array[PsiTypeParameter] = null

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