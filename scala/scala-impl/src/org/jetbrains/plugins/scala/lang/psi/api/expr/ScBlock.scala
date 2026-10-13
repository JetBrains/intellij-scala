package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLineNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypePropagation
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScImportsHolder, ScalaPsiUtil}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

trait ScBlock extends ScExpression
  with ScDeclarationSequenceHolder
  with ScImportsHolder
  with ScOptionalBracesOwner
{
  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    if (hasCaseClauses) {
      getContext match {
        case _: ScCatchBlock =>
          // The bodies of the case clauses of a catch block are typed against the expected type of the try expression,
          // which isn't derived from the expected type of the block, see ScTry
          val tryExpression = getContext.getContext.asInstanceOf[ScTry]
          return catchBlockType(tryExpression.expectedType())
        case _ =>
          val et = expectedType
            .getOrElse(return Failure(ScalaBundle.message("cannot.infer.type.without.expected.type")))

          val paramInfo = extractExpectedTypeParams(et)

          return paramInfo match {
            case Some((params, isPartial)) =>
              val clausesLubType = caseClausesLubType { (clause, body) =>
                ExpectedTypePropagation.partialFunctionCaseClauseBody(clause, body, ExpectedTypePropagation.nested(et, body))
              }

              val sanitizedParams = params.map(_.removeVarianceAbstracts())

              val tpe =
                if (isPartial) PartialFunctionType(clausesLubType, sanitizedParams.head)
                else           FunctionType(clausesLubType, sanitizedParams)

              Right(tpe)
            case None =>
              Failure(ScalaBundle.message("cannot.infer.type.without.function.expected.type"))
          }
      }
    }
    val inner = resultExpression match {
      case None =>
        ScalaPsiUtil.fileContext(this) match {
          case scalaFile: ScalaFile if scalaFile.isCompiled => Nothing
          case _ => Unit
        }
      case Some(e) => e.`type`(ExpectedTypePropagation.blockResult(this, e, expectedType)).getOrAny
    }
    Right(inner)
  }

  /**
   * The type of this block of case clauses, if it is the block of a catch block.
   *
   * @param tryExpectedType the expected type of the try expression, see [[ScTry]]
   */
  def catchBlockType(tryExpectedType: Option[ScType]): TypeResult = {
    implicit val resolveScope: GlobalSearchScope = this.resolveScope

    val clausesLubType = caseClausesLubType((_, _) => tryExpectedType)

    val manager = ScalaPsiManager.instance
    val funs = manager.getCachedClasses(resolveScope, PartialFunctionType.TypeName)
    val fun = funs.find(_.is[ScTrait]).getOrElse(return Failure(ScalaBundle.message("cannot.find.partialfunction.class")))
    val throwable = manager.getCachedClass(resolveScope, "java.lang.Throwable").orNull
    if (throwable == null) return Failure(ScalaBundle.message("cannot.find.throwable.class"))
    Right(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesLubType)))
  }

  /**
   * @param clauseExpectedType computes the expected type of the body of a case clause
   */
  private def caseClausesLubType(clauseExpectedType: (ScCaseClause, ScExpression) => Option[ScType]): ScType = {
    val caseClauses = findChild[ScCaseClauses].get
    val clauses: Seq[ScCaseClause] = caseClauses.caseClauses

    val clausesTypes = ArrayBuffer[ScType]()
    val iterator = clauses.iterator
    while (iterator.hasNext) {
      val clause = iterator.next()
      clause.expr match {
        case Some(e) => clausesTypes += e.`type`(clauseExpectedType(clause, e)).getOrNothing
        case _ =>
      }
    }

    if (clausesTypes.isEmpty) Nothing
    else                      clausesTypes.lub()
  }

  @tailrec
  private def extractExpectedTypeParams(pt: ScType): Option[(Seq[ScType], Boolean)] = pt match {
    case FunctionType(_, params)       => Option(params -> false)
    case PartialFunctionType(_, param) => Option(Seq(param) -> true)
    case ContextFunctionType(ret, _)   => extractExpectedTypeParams(ret)
    case _                             => None
  }

  def hasCaseClauses: Boolean = false
  def isInCatchBlock: Boolean = getContext.is[ScCatchBlock]
  def isPartialFunction: Boolean = hasCaseClauses && !isInCatchBlock

  def exprs: Seq[ScExpression] = findChildren[ScExpression]
  def statements: Seq[ScBlockStatement] = findChildren[ScBlockStatement]

  def hasRBrace: Boolean = getRBrace.isDefined
  def hasLBrace: Boolean = getLBrace.isDefined

  def resultExpression: Option[ScExpression] = lastStatement.flatMap(_.asOptionOf[ScExpression])
  def lastStatement: Option[ScBlockStatement] = findLastChild[ScBlockStatement]

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(createNewLineNode(), before.getNode)
    true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean =
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      processDeclarationsFromImports(processor, state, lastParent, place)

  def needCheckExpectedType = true
}

object ScBlock {
  def unapplySeq(block: ScBlock): Option[Seq[ScBlockStatement]] = Option(block.statements)
}
