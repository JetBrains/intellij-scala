package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScParamElementType
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

class ScParameterImpl protected (stub: ScParameterStub, nodeType: ScParamElementType[_ <: ScParameter], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScParameter {

  def this(node: ASTNode) = this(null, null, node)

  def this(stub: ScParameterStub) = this(stub, ScalaElementTypes.PARAM, null)

  override def toString: String = "Parameter: " + name

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isCallByNameParameter: Boolean = byStubOrPsi(_.isCallByNameParameter)(paramType.exists(_.isCallByNameParameter))

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def deprecatedName: Option[String] = byStubOrPsi(_.deprecatedName) {
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

  def typeElement: Option[ScTypeElement] = byPsiOrStub(paramType.flatMap(_.typeElement.toOption))(_.typeElement)

  def getType(ctx: TypingContext): TypeResult[ScType] = {
    def success(t: ScType): TypeResult[ScType] = Success(t, Some(this))
    //todo: this is very error prone way to calc type, when usually we need real parameter type
    getStub match {
      case null =>
        typeElement match {
          case None if baseDefaultParam =>
            getActualDefaultExpression match {
              case Some(t) => success(t.getType(TypingContext.empty).getOrNothing)
              case None => success(Nothing)
            }
          case None => expectedParamType.map(_.unpackedType) match {
            case Some(t) => success(t)
            case None => success(Nothing)
          }
          case Some(e) => success(e.getType(TypingContext.empty).getOrAny)
        }
      case paramStub =>
        paramStub.typeText match {
          case None if paramStub.getParentStub != null && paramStub.getParentStub.getParentStub != null &&
            paramStub.getParentStub.getParentStub.getParentStub.isInstanceOf[ScFunctionStub] =>
            Failure("Cannot infer type", Some(this))
          case None => Failure("Wrong Stub problem", Some(this)) //shouldn't be
          case Some(_: String) => paramStub.typeElement match {
            case Some(te) => te.getType(TypingContext.empty)
            case None => Failure("Wrong type element", Some(this))
          }
        }
    }
  }

  def baseDefaultParam: Boolean = byStubOrPsi(_.isDefaultParameter)(findChildByType(ScalaTokenTypes.tASSIGN) != null)

  def isRepeatedParameter: Boolean = byStubOrPsi(_.isRepeated)(paramType.exists(_.isRepeatedParameter))

  def getActualDefaultExpression: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

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
