package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
 * @author ilyas
 */

abstract class ScFunctionImpl protected (stub: ScFunctionStub, nodeType: ScFunctionElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScMember
    with ScFunction with ScTypeParametersOwner {

  override def isStable = false

  def nameId: PsiElement = {
    val n = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case null => getNode.findChildByType(ScalaTokenTypes.kTHIS)
      case notNull => notNull
    }
    if (n == null) {
      val stub = getGreenStub
      if (stub == null) {
        val message = s"Both stub and name identifier node are null for ${getClass.getSimpleName} \n$getText"
        throw new NullPointerException(message)
      }
      return createIdentifier(getGreenStub.getName).getPsi
    }
    n.getPsi
  }

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def paramClauses: ScParameters = getStubOrPsiChild(ScalaElementTypes.PARAM_CLAUSES)

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (lastParent == null) return true

    // process function's type parameters
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)) return false

    processParameters(processor, state, lastParent)
  }

  private def processParameters(processor: PsiScopeProcessor,
                                state: ResolveState,
                                lastParent: PsiElement): Boolean = {

    if (lastParent != null && shouldProcessParameters(lastParent)) {
      for {
        clause <- effectiveParameterClauses
        param  <- clause.effectiveParameters
      } {
        ProgressManager.checkCanceled()
        if (!processor.execute(param, state)) return false
      }
    }
    true
  }

  // to resolve parameters in return type, type parameter context bounds and body;
  // references in default parameters are processed in ScParametersImpl
  protected def shouldProcessParameters(lastParent: PsiElement): Boolean = {
    def isSynthetic = lastParent.getContext != lastParent.getParent
    def isFromTypeParams = lastParent.isInstanceOf[ScTypeParamClause]

    //don't compare returnTypeElement with lastParent, they may be different instances due to caches/stubs
    def isReturnTypeElement = lastParent.isInstanceOf[ScTypeElement] && lastParent.getContext == this

    isSynthetic || isFromTypeParams || isReturnTypeElement
  }

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def returnTypeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)
}