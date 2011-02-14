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
import api.toplevel.typedef.ScClass
import types.Conformance.AliasType

/**
 * @author Alexander Podkhalyuzin
 */

class ScParameterImpl extends ScalaStubBasedElementImpl[ScParameter] with ScParameter {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScParameterStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "Parameter"

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isCallByNameParameter: Boolean = {
    paramType match {
      case Some(paramType) => {
        paramType.isCallByNameParameter
      }
      case _ => false
    }
  }

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def getRealParameterType(ctx: TypingContext): TypeResult[ScType] = {
    if (!isRepeatedParameter) return getType(ctx)
    getType(ctx) match {
      case f@Success(tp: ScType, elem) => {
        val seq = JavaPsiFacade.getInstance(getProject).findClass("scala.collection.Seq", getResolveScope)
        if (seq != null) {
          return Success(new ScParameterizedType(new ScDesignatorType(seq), Seq(tp)), elem)
        } else return f
      }
      case f => return f
    }
  }

  def nameId = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) findChildByType(ScalaTokenTypes.tUNDER) else id
  }

  def paramType: Option[ScParameterType] = findChild(classOf[ScParameterType])

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner], classOf[ScFunctionExpr])

  def getTypeElement = null

  def typeElement = paramType match {
    case Some(x) if x.typeElement != null => Some(x.typeElement)
    case _ => None
  }

  override def getUseScope = {
    getDeclarationScope match {
      case null => GlobalSearchScope.EMPTY_SCOPE
      case expr: ScFunctionExpr => new LocalSearchScope(expr)
      case clazz: ScClass if clazz.isCase => clazz.getUseScope
      case clazz: ScClass if isInstanceOf[ScClassParameter] && !asInstanceOf[ScClassParameter].isVal &&
              !asInstanceOf[ScClassParameter].isVar => {
        new LocalSearchScope(clazz)
      }
      case d => d.getUseScope
    }
  }

  def isVarArgs = isRepeatedParameter

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
          case str: String => stub.asInstanceOf[ScParameterStub].getTypeElement match {
            case Some(te) => return te.getType(TypingContext.empty)
            case None => return Failure("Wrong type element", Some(this))
          }
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

  private def expectedParamType: Option[ScType] = getContext match {
    case clause: ScParameterClause => clause.getParent.getParent match {
      // For parameter of anonymous functions to infer parameter's type from an appropriate
      // an. fun's type
      case f: ScFunctionExpr => {
        var result: Option[ScType] = null //strange logic to handle problems with detecting type
        for (tp <- f.expectedTypes if result != None) {
          def applyForFunction(tp: ScType, checkDeep: Boolean) {
            tp match {
              case ScFunctionType(ret, _) if checkDeep => applyForFunction(ret, false)
              case ScFunctionType(_, params) if params.length == f.parameters.length => {
                val i = clause.parameters.indexOf(this)
                if (result != null) result = None
                else result = Some(params(i).removeAbstracts)
              }
              case _: ScFunctionType => //nothing to do
              case p: ScParameterizedType if p.getFunctionType != None => {
                applyForFunction(p.getFunctionType.get, checkDeep)
              }
              case _ => {
                Conformance.isAliasType(tp) match {
                  case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => {
                    val res: TypeResult[ScType] = ta.aliasedType
                    if (!res.isEmpty) {
                      applyForFunction(res.get, checkDeep)
                    }
                  }
                  case _ =>
                }
              }
            }
          }
          applyForFunction(tp, ScUnderScoreSectionUtil.underscores(f).length > 0)
        }
        if (result == null) result = None
        result
      }
      case _ => None
    }
  }

  def getTypeNoResolve: PsiType = PsiType.VOID

  def isDefaultParam: Boolean = {
    if (baseDefaultParam) return true
    getSuperParameter match {
      case Some(param) => param.isDefaultParam
      case _ => false
    }
  }

  def baseDefaultParam: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isDefaultParam
    }
    findChildByType(ScalaTokenTypes.tASSIGN) != null
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

  def getSuperParameter: Option[ScParameter] = {
    getParent match {
      case clause: ScParameterClause => {
        val i = clause.parameters.indexOf(this)
        clause.getParent match {
          case p: ScParameters => {
            val j = p.clauses.indexOf(clause)
            p.getParent match {
              case fun: ScFunction => {
                fun.superMethod match {
                  case Some(method: ScFunction) => {
                    val clauses: Seq[ScParameterClause] = method.paramClauses.clauses
                    if (j >= clauses.length) return None
                    val parameters: Seq[ScParameter] = clauses.apply(j).parameters
                    if (i >= parameters.length) return None
                    return Some(parameters.apply(i))
                  }
                  case _ => return None
                }
              }
              case _ => return None
            }
          }
          case _ => return None
        }
      }
      case _ => return None
    }
  }

  def getDefaultExpression: Option[ScExpression] = {
    val res = findChild(classOf[ScExpression])
    if (res == None) {
      getSuperParameter.flatMap(_.getDefaultExpression)
    } else res
  }
}
