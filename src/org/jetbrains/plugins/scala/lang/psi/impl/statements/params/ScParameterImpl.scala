package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

class ScParameterImpl protected (stub: StubElement[ScParameter], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScParameter {
  def this(node: ASTNode) = {this(null, null, node)}

  def this(stub: ScParameterStub) = {this(stub, ScalaElementTypes.PARAM, null)}

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

  def nameId: PsiElement = {
    val id = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)
    if (id == null) findChildByType[PsiElement](ScalaTokenTypes.tUNDER) else id
  }

  def getTypeElement = null

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].typeElement
    }
    paramType match {
      case Some(x) if x.typeElement != null => Some(x.typeElement)
      case _ => None
    }
  }

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    //todo: this is very error prone way to calc type, when usually we need real parameter type
    val computeType: ScType = {
      val stub = getStub
      if (stub != null) {
        stub.asInstanceOf[ScParameterStub].typeText match {
          case None if stub.getParentStub != null && stub.getParentStub.getParentStub != null &&
                  stub.getParentStub.getParentStub.getParentStub.isInstanceOf[ScFunctionStub] => return Failure("Cannot infer type", Some(this))
          case None => return Failure("Wrong Stub problem", Some(this)) //shouldn't be
          case Some(_: String) => stub.asInstanceOf[ScParameterStub].typeElement match {
            case Some(te) => return te.getType(TypingContext.empty)
            case None => return Failure("Wrong type element", Some(this))
          }
        }
      } else {
        typeElement match {
          case None if baseDefaultParam =>
            getActualDefaultExpression match {
              case Some(t) => t.getType(TypingContext.empty).getOrNothing
              case None => Nothing
            }
          case None => expectedParamType.map(_.unpackedType) match {
            case Some(t) => t
            case None => api.Nothing
          }
          case Some(e) => e.getType(TypingContext.empty).getOrAny
        }
      }
    }
    Success(computeType, Some(this))
  }

  def baseDefaultParam: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].isDefaultParameter
    }
    findChildByType[PsiElement](ScalaTokenTypes.tASSIGN) != null
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

  def getActualDefaultExpression: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScParameterStub].bodyExpression
    }
    findChild(classOf[ScExpression])
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
