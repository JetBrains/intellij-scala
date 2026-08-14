package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ContextFunctionType, FunctionType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Widening, api}
import org.jetbrains.plugins.scala.util.SAMUtil

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

  private[this] def widenSingletonsInRetType(retType: ScType): ScType = {
    // The expected type doesn't have to be a function type, it may also be a SAM type, whose
    // abstract method is what determines the expected result type, as in
    // `((x => x): T)` for a `trait T { def apply(x: s.type): s.type }`
    def asFunctionType(expected: ScType): Option[ScType] = expected match {
      case functionType @ FunctionType(_, _) => Option(functionType)
      case _                                 => SAMUtil.SAMToFunctionType(expected, this)
    }

    val expectedRetTpe = this.expectedType().flatMap(asFunctionType).collect {
      // The result type is in covariant position, so an expected type that isn't fully determined
      // yet only bounds it from above
      case FunctionType(expectedRetTpe, _) => expectedRetTpe.removeVarianceAbstracts()
    }.filter {
      // An expected result type that is still an undetermined type parameter says nothing about the
      // result type, and since it conforms to anything, `Singleton` included, it would suppress
      // widening altogether
      case _: UndefinedType => false
      case _                => true
    }

    Widening.widenInferred(retType, expectedRetTpe)
  }

  protected override def innerType: TypeResult = {
    val paramTypes      = parameters.map(_.`type`().getOrNothing)
    val maybeResultType = result.map(r => widenSingletonsInRetType(r.`type`().getOrAny))

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