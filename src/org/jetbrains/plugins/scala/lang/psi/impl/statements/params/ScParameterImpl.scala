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
import org.jetbrains.plugins.scala.lang.psi.types._
import result.{Failure, Success, TypingContext, TypeResult}
import api.toplevel.typedef.ScClass
import api.base.types.ScTypeElement
import collection.mutable.ArrayBuffer
import api.ScalaElementVisitor
import collection.immutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import scala.annotation.tailrec

/**
 * @author Alexander Podkhalyuzin
 */

class ScParameterImpl extends ScalaStubBasedElementImpl[ScParameter] with ScParameter {
  def this(node: ASTNode) = {
    this(); setNode(node)
  }

  def this(stub: ScParameterStub) = {
    this(); setStub(stub); setNode(null)
  }

  override def toString: String = "Parameter: " + name

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isCallByNameParameter: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isCallByNameParameter
    }
    paramType match {
      case Some(paramType) =>
        paramType.isCallByNameParameter
      case _ => false
    }
  }

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def deprecatedName: Option[String] = {
    val stub = getStub
    if (stub != null) return stub.asInstanceOf[ScParameterStub].deprecatedName
    annotations.find(_.typeElement.getText.contains("deprecatedName")) match {
      case Some(deprecationAnnotation) =>
        deprecationAnnotation.constructor.args.flatMap {
          case args =>
            val exprs = args.exprs
            if (exprs.length != 1) None
            else {
              exprs(0) match {
                case literal: ScLiteral if literal.getNode.getFirstChildNode != null &&
                        literal.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tSYMBOL =>
                  val literalText = literal.getText
                  if (literalText.length < 2) None
                  else Some(literalText.substring(1))
                case _ => None
              }
            }
        }
      case None => None
    }
  }

  def getRealParameterType(ctx: TypingContext): TypeResult[ScType] = {
    if (!isRepeatedParameter) return getType(ctx)
    getType(ctx) match {
      case f@Success(tp: ScType, elem) =>
        val seq = ScalaPsiManager.instance(getProject).getCachedClass("scala.collection.Seq", getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
        if (seq != null) {
          Success(new ScParameterizedType(ScType.designator(seq), Seq(tp)), elem)
        } else f
      case f => f
    }
  }

  def nameId = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) findChildByType(ScalaTokenTypes.tUNDER) else id
  }

  def paramType: Option[ScParameterType] = findChild(classOf[ScParameterType])

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner], classOf[ScFunctionExpr])

  def getTypeElement = null

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].getTypeElement
    }
    paramType match {
      case Some(x) if x.typeElement != null => Some(x.typeElement)
      case _ => None
    }
  }

  def isVarArgs = isRepeatedParameter

  def computeConstantValue = null

  def normalizeDeclaration() {}

  def hasInitializer = false

  def getInitializer = null

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    //todo: this is very error prone way to calc type, when usually we need real parameter type
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
          case None if baseDefaultParam =>
            getActualDefaultExpression match {
              case Some(t) => t.getType(TypingContext.empty).getOrNothing
              case None => lang.psi.types.Nothing
            }
          case None => expectedParamType.map(_.unpackedType) match {
            case Some(t) => t
            case None => lang.psi.types.Nothing
          }
          case Some(e) => e.getType(TypingContext.empty).getOrAny
        }
      }
    }
    Success(computeType, Some(this))
  }

  def getType: PsiType = ScType.toPsi(getRealParameterType(TypingContext.empty).getOrNothing, getProject, getResolveScope)

  def expectedParamType: Option[ScType] = getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      // For parameter of anonymous functions to infer parameter's type from an appropriate
      // an. fun's type
      case f: ScFunctionExpr =>
        var result: Option[ScType] = null //strange logic to handle problems with detecting type
        for (tp <- f.expectedTypes(fromUnderscore = false) if result != None) {
          @tailrec
          def applyForFunction(tp: ScType, checkDeep: Boolean) {
            tp.removeAbstracts match {
              case ScFunctionType(ret, _) if checkDeep => applyForFunction(ret, checkDeep = false)
              case ScFunctionType(_, params) if params.length == f.parameters.length =>
                val i = clause.parameters.indexOf(this)
                if (result != null) result = None
                else result = Some(params(i))
              case _ =>
            }
          }
          applyForFunction(tp, ScUnderScoreSectionUtil.underscores(f).length > 0)
        }
        if (result == null || result == None) result = None //todo: x => foo(x)
        result
      case _ => None
    }
  }

  def getTypeNoResolve: PsiType = PsiType.VOID

  def isDefaultParam: Boolean = {
    @tailrec
    def check(param: ScParameter, visited: HashSet[ScParameter]): Boolean = {
      if (param.baseDefaultParam) return true
      if (visited.contains(param)) return false
      getSuperParameter match {
        case Some(superParam) =>
          check(superParam, visited + param)
        case _ => false
      }
    }
    check(this, HashSet.empty)
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
      case clause: ScParameterClause =>
        val i = clause.parameters.indexOf(this)
        clause.getParent match {
          case p: ScParameters =>
            val j = p.clauses.indexOf(clause)
            p.getParent match {
              case fun: ScFunction =>
                fun.superMethod match {
                  case Some(method: ScFunction) =>
                    val clauses: Seq[ScParameterClause] = method.paramClauses.clauses
                    if (j >= clauses.length) return None
                    val parameters: Seq[ScParameter] = clauses.apply(j).parameters
                    if (i >= parameters.length) return None
                    Some(parameters.apply(i))
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  def getActualDefaultExpression: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].getDefaultExpr
    }
    findChild(classOf[ScExpression])
  }

  def getDefaultExpression: Option[ScExpression] = {
    val res = getActualDefaultExpression
    if (res == None) {
      getSuperParameter.flatMap(_.getDefaultExpression)
    } else res
  }

  def remove() {
    val node = getNode
    val toRemove: ArrayBuffer[ASTNode] = ArrayBuffer.apply(node)
    getParent match {
      case clause: ScParameterClause =>
        val index = clause.parameters.indexOf(this)
        val length = clause.parameters.length
        if (length != 1) {
          if (index != length) {
            var n = node.getTreeNext
            while (n != null && n.getElementType != ScalaTokenTypes.tRPARENTHESIS &&
                    !n.getPsi.isInstanceOf[ScParameter]) {
              toRemove += n
              n = n.getTreeNext
            }
          } else {
            var n = node.getTreePrev
            while (n != null && n.getElementType != ScalaTokenTypes.tLPARENTHESIS &&
                    !n.getPsi.isInstanceOf[ScParameter]) {
              toRemove += n
              n = n.getTreePrev
            }
          }
        }
      case _ =>
    }
    for (elem <- toRemove) {
      elem.getTreeParent.removeChild(elem)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitParameter(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitParameter(this)
      case _ => super.accept(visitor)
    }
  }
}
