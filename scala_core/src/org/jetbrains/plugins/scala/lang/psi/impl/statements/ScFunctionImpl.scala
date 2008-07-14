package org.jetbrains.plugins.scala.lang.psi.impl.statements


import api.expr.ScAnnotations
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * @author ilyas
 */

abstract class ScFunctionImpl(node: ASTNode) extends ScMemberImpl(node) with ScFunction with ScTypeParametersOwner {

  def nameId() = {
    val n = node.findChildByType(ScalaTokenTypes.tIDENTIFIER)
    (if (n == null) {
      node.findChildByType(ScalaTokenTypes.kTHIS)
    } else n).getPsi
  }


  def paramClauses: ScParameters = findChildByClass(classOf[ScParameters])

  def parameters: Seq[ScParameter] = {
    val pcs = getParameterList
    if (pcs != null) pcs.params else Seq.empty
  }

  override def getIcon(flags: Int) = Icons.FUNCTION

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

  def getThrowsList = findChildByClass(classOf[ScAnnotations])

  def getTypeParameters = PsiTypeParameter.EMPTY_ARRAY

  def getTypeParameterList = null

  def hasTypeParameters = false

  def getParameterList: ScParameters = {
    findChild(classOf[ScParameters]) match {
      case None => ScalaPsiElementFactory.createDummyParams(this.getManager)
      case Some(x) => x
    }
  }

  // Fake method to implement simple application running
  def isMainMethod: Boolean = false

  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!processor.execute(this, state)) return false

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }
}