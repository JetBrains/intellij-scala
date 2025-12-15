package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ContextFunctionType, FunctionType, Nothing, Singleton}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{FunctionLikeType, ScLiteralType, ScType, api}

class ScFunctionExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScFunctionExpr {

  override def parameters: Seq[ScParameter] = params.params

  override def params: ScParameters = findChildByClass(classOf[ScParameters])

  override def result: Option[ScExpression] = findChild[ScExpression]

  override def hasParentheses: Boolean = leftParen.isDefined && rightParen.isDefined

  override def leftParen: Option[PsiElement] = params.clauses.head.getFirstChild match {
    case e: LeafPsiElement if e.textMatches("(") => Some(e)
    case _                                       => None
  }

  override def rightParen: Option[PsiElement] = params.clauses.head.getLastChild match {
    case e: LeafPsiElement if e.textMatches(")") => Some(e)
    case _                                       => None
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    result match {
      case Some(x) if x == lastParent || (lastParent.isInstanceOf[ScalaPsiElement] &&
        x == lastParent.asInstanceOf[ScalaPsiElement].getDeepSameElementInContext) =>
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
        true
      case _ => true
    }
  }

  private[this] def widenSingletonsInRetType(retType: ScType): ScType = retType match {
    case lit: ScLiteralType =>
      this.expectedType() match {
        case Some(FunctionType(expectedRetTpe, _)) =>
          val eTpe = expectedRetTpe.removeAbstracts
          if (!eTpe.isNothing && eTpe.conforms(Singleton)) lit
          else                                             lit.widen
        case _ => lit.widen
      }
    case tpe => tpe
  }

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    val actualExpectedType = expectedType.orElse(this.expectedType())
    val functionLike       = FunctionLikeType(this)

    lazy val defaultPt = (parameters.map(_ => Nothing), Any)

    val (paramsPt, resPt) = actualExpectedType match {
      case Some(functionLike(_, res, params)) if params.length == parameters.length => (params, res)
      case _                                                                        => defaultPt
    }

    val paramTypes = parameters.zip(paramsPt).map { case (param, pt) =>
      param.`type`(Option(pt)).getOrNothing
    }

    val maybeResultType = result.map(
      r => widenSingletonsInRetType(r.`type`(Option(resPt)).getOrAny)
    )

    val functionTypeFactory =
      if (isContext) ContextFunctionType
      else           FunctionType

    val functionType = functionTypeFactory(maybeResultType.getOrElse(api.Unit), paramTypes)
    Right(functionType)
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "FunctionExpression"

  override def isContext: Boolean =
    findChildByType[PsiElement](ScalaTokenType.ImplicitFunctionArrow) ne null
}