package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import psi.stubs._
import api.statements.params._
import api.statements._
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.util._
import toplevel.synthetic.JavaIdentifier
import com.intellij.psi._
import api.expr._
import psi.types.{ScParameterizedType, ScType, ScFunctionType}
import org.jetbrains.plugins.scala.lang.psi.types._
import result.{Failure, Success, TypingContext, TypeResult}

/**
 * @author Alexander Podkhalyuzin
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

  def getTypeElement = null

  def typeElement = paramType match {
    case Some(x) if x.typeElement != null => Some(x.typeElement)
    case _ => None
  }

  override def getUseScope = {
    val scope = getDeclarationScope
    if (scope != null) new LocalSearchScope(scope) else GlobalSearchScope.EMPTY_SCOPE
  }

  def isVarArgs = false

  def computeConstantValue = null

  def normalizeDeclaration() = false

  def hasInitializer = false

  def getInitializer = null

  // todo rewrite to handle errors
  def getType(ctx: TypingContext) : TypeResult[ScType] = {
    val computeType: ScType = {
      val stub = getStub
      if (stub != null) {
        stub.asInstanceOf[ScParameterStub].getTypeText match {
          case "" if stub.getParentStub != null && stub.getParentStub.getParentStub != null &&
                  stub.getParentStub.getParentStub.getParentStub.isInstanceOf[ScFunctionStub] => return Failure("Cannot infer type", Some(this))
          case "" => return Failure("Wrong Stub problem", Some(this)) //shouldn't be
          case str: String => ScalaPsiElementFactory.createTypeFromText(str, this)
        }
      } else {
        typeElement match {
          case None => expectedParamType match {
            case Some(t) => t
            case None => lang.psi.types.Nothing
          }
          case Some(e) => e.getType(TypingContext.empty) getOrElse Any
        }
      }
    }
    Success(computeType, Some(this))
  }

  def getType : PsiType = ScType.toPsi(getType(TypingContext.empty) getOrElse Nothing, getProject, getResolveScope)

  private def expectedParamType: Option[ScType] = getParent match {
    case clause: ScParameterClause => clause.getParent.getParent match {
      // For parameter of anonymous functions to infer parameter's type from an appropriate
      // an. fun's type
      case f: ScFunctionExpr => {
        var result: Option[ScType] = null //strange logic to handle problems with detecting type
        for (tp <- f.expectedTypes if result != None) {
          tp match {
            case ScFunctionType(_, params) if params.length == f.parameters.length => {
              val i = clause.parameters.indexOf(this)
              if (result != null) result = None
              else result = Some(params(i))
            }
            case ScParameterizedType(t, args) => {
              ScType.extractDesignated(t) match { //todo: this is hack, scala.Function1 ScProjectionType?
                case Some((c: PsiClass, _)) if c.getQualifiedName == "scala.Function" + f.parameters.length => {
                  val i = clause.parameters.indexOf(this)
                  if (result != null) result = None
                  else result = Some(args(i))
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
        if (result == null) result = None
        result
      }
      case _ => None
    }
  }

  def getTypeNoResolve: PsiType = PsiType.VOID

  def isDefaultParam: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isDefaultParam
    }
    return findChildByType(ScalaTokenTypes.tASSIGN) != null
  }

  def isRepeatedParameter: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isRepeated
    }
    paramType match {
      case Some(p: ScParameterType) => p.isRepeatedParameter
      case None => false
    }
  }
}
