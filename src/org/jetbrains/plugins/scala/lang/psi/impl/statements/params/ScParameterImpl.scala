package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import psi.stubs._
import psi.types.{ScType, ScFunctionType}
import api.base._
import api.expr.ScFunctionExpr
import api.statements.params._
import api.statements._
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import icons.Icons
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.util._
import toplevel.synthetic.JavaIdentifier
import com.intellij.psi._

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScParameterImpl extends ScalaStubBasedElementImpl[ScParameter] with ScParameter {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScParameterStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "Parameter"

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def nameId = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) findChildByType(ScalaTokenTypes.tUNDER) else id
  }

  def paramType = findChild(classOf[ScParameterType])

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner])

  def getAnnotations = PsiAnnotation.EMPTY_ARRAY

  def getTypeElement = null

  def typeElement = paramType match {
    case Some(x) if x.typeElement != null => Some(x.typeElement)
    case _ => None
  }

  override def getUseScope = {
    val scope = getDeclarationScope
    if (scope != null) new LocalSearchScope(scope) else GlobalSearchScope.EMPTY_SCOPE
  }

  def calcType: ScType = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScParameterStub].getTypeText match {
        case "" if stub.getParentStub != null && stub.getParentStub.getParentStub != null &&
                   stub.getParentStub.getParentStub.getParentStub.isInstanceOf[ScFunctionStub] => return lang.psi.types.Nothing
        case "" => //shouldn't be
        case str: String => return ScalaPsiElementFactory.createTypeFromText(str, this)
      }
    }
    typeElement match {
      case None => expectedParamType match {
        case Some(t) => t
        case None => lang.psi.types.Nothing
      }
      case Some(e) => e.cashedType
    }
  }

  def isVarArgs = false

  def computeConstantValue = null

  def normalizeDeclaration() = false

  def hasInitializer = false

  def getInitializer = null

  def getType: PsiType = ScType.toPsi(calcType, getProject, getResolveScope)

  private def expectedParamType: Option[ScType] = getParent match {
    case clause: ScParameterClause => clause.getParent.getParent match {
      // For parameter of anonymous functions to infer parameter's type from an appropriate
      // an. fun's type
      case f: ScFunctionExpr => f.expectedType.map({
        case ScFunctionType(_, params) =>
          val i = clause.parameters.indexOf(this)
          if (i >= 0 && i < params.length) params(i) else psi.types.Nothing
        case _ => psi.types.Nothing
      })
      case _ => None
    }
  }
}
