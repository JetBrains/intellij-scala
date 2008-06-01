package org.jetbrains.plugins.scala.lang.psi.impl.statements


import java.util._
import com.intellij.lang._
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.icons._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
 * @author ilyas
 */

abstract class ScFunctionImpl(node: ASTNode) extends ScMemberImpl(node) with ScFunction {

  def getId = {
    val n = node.findChildByType(ScalaTokenTypes.tIDENTIFIER)
    (if (n == null) {
      node.findChildByType(ScalaTokenTypes.kTHIS)
    } else n).getPsi
  }

  override def getName = getId.getText

  override def getIcon(flags: Int) = Icons.FUNCTION

  def paramClauses: ScParameters = findChildByClass(classOf[ScParameters])

  def getReturnScTypeElement: ScTypeElement = findChildByClass(classOf[ScTypeElement])

  def parameters: Seq[ScParameter] = {
    val pcs = getParameterList
    if (pcs != null) pcs.params else Seq.empty
  }

  override def getNavigationElement: PsiElement = getId

  def getReturnType = if (isMainMethod) PsiType.VOID else null

  def getNameIdentifier = null

  def getReturnTypeElement = null

  def getHierarchicalMethodSignature = null

  def findSuperMethods(parentClass: PsiClass) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods(checkAccess: Boolean) = PsiMethod.EMPTY_ARRAY

  def findSuperMethods = PsiMethod.EMPTY_ARRAY

  def findDeepestSuperMethod = null

  def findDeepestSuperMethods = PsiMethod.EMPTY_ARRAY

  def getPom = null

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean) =
    new ArrayList[MethodSignatureBackedByPsiMethod]()

  def getSignature(substitutor: PsiSubstitutor) = MethodSignatureBackedByPsiMethod.create(this, substitutor)

  override def hasModifierProperty(prop: String) =
    if (isMainMethod && prop == PsiModifier.STATIC || prop == PsiModifier.PUBLIC) true
    else super.hasModifierProperty(prop)

  //todo implement me!
  def isVarArgs = false

  def isConstructor = false

  def getBody = null

  def getThrowsList = null

  def getTypeParameters = PsiTypeParameter.EMPTY_ARRAY

  def getTypeParameterList = null

  def hasTypeParameters = false

  def getParameterList = findChildByClass(classOf[ScParameters])

  // Fake method to implement simple application running
  def isMainMethod: Boolean = false

}