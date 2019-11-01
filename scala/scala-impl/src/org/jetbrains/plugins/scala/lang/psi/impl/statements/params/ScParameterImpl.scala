package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScParamElementType
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alexander Podkhalyuzin
 */

class ScParameterImpl protected (stub: ScParameterStub, nodeType: ScParamElementType[_ <: ScParameter], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScParameter {

  def this(node: ASTNode) = this(null, null, node)

  def this(stub: ScParameterStub) = this(stub, ScalaElementType.PARAM, null)

  override def toString: String = "Parameter: " + ifReadAllowed(name)("")

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isCallByNameParameter: Boolean = byStubOrPsi(_.isCallByNameParameter)(paramType.exists(_.isCallByNameParameter))

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def deprecatedName: Option[String] = byStubOrPsi(_.deprecatedName) {
    // by-text heuristic is used because this method is called during stub creation,
    // so actual resolving of an annotation causes deadlock

    for {
      annotation <- annotations.find(_.typeElement.getText.contains("deprecatedName"))
      args <- annotation.constructorInvocation.args
      ScSymbolLiteral(symbol) <- args.exprs.headOption
    } yield symbol.name
  }

  def nameId: PsiElement = {
    val id = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)
    if (id == null) findChildByType[PsiElement](ScalaTokenTypes.tUNDER) else id
  }

  def getTypeElement: PsiTypeElement = null

  def typeElement: Option[ScTypeElement] = byPsiOrStub(paramType.flatMap(_.typeElement.toOption))(_.typeElement)

  def `type`(): TypeResult = {
    def success(t: ScType): TypeResult = Right(t)
    //todo: this is very error prone way to calc type, when usually we need real parameter type
    getStub match {
      case null =>
        typeElement match {
          case None if baseDefaultParam =>
            getActualDefaultExpression match {
              case Some(t) => success(t.`type`().getOrNothing)
              case None => success(Nothing)
            }
          case None => expectedParamType.map(_.unpackedType) match {
            case Some(t) => success(t)
            case None => success(Nothing)
          }
          case Some(e) => e.`type`()
        }
      case paramStub =>
        paramStub.typeText match {
          case None if paramStub.getParentStub != null && paramStub.getParentStub.getParentStub != null &&
            paramStub.getParentStub.getParentStub.getParentStub.isInstanceOf[ScFunctionStub[_]] =>
            Failure("Cannot infer type")
          case None => Failure("Wrong Stub problem") //shouldn't be
          case Some(_: String) => paramStub.typeElement match {
            case Some(te) => te.`type`()
            case None => Failure("Wrong type element")
          }
        }
    }
  }

  def baseDefaultParam: Boolean = byStubOrPsi(_.isDefaultParameter)(findChildByType(ScalaTokenTypes.tASSIGN) != null)

  def isRepeatedParameter: Boolean = byStubOrPsi(_.isRepeated)(paramType.exists(_.isRepeatedParameter))

  def getActualDefaultExpression: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  override def getNavigationElement: PsiElement = {
    val maybeResult = owner match {
      case m: ScMethodLike =>
        m.getNavigationElement match {
          case `m` => None
          case other: ScMethodLike =>
            other.effectiveParameterClauses
              .flatMap(_.effectiveParameters)
              .find(_.name == name)
          case _ => None
        }
      case _ => None
    }

    maybeResult.getOrElse {
      ScalaPsiUtil.withOriginalContextBound(this)(this: PsiElement) {
        case (_, bound, _) => bound
      }
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor) {
    visitor.visitParameter(this)
  }
}
