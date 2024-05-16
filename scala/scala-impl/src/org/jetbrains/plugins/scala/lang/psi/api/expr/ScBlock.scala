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
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScImportsHolder, ScalaPsiUtil}

import scala.collection.mutable.ArrayBuffer

trait ScBlock extends ScExpression
  with ScDeclarationSequenceHolder
  with ScImportsHolder
  with ScOptionalBracesOwner
{
  protected override def innerType: TypeResult = {
    if (hasCaseClauses) {
      val caseClauses = findChild[ScCaseClauses].get
      val clauses: Seq[ScCaseClause] = caseClauses.caseClauses

      val clausesTypes = ArrayBuffer[ScType]()
      val iterator = clauses.iterator
      while (iterator.hasNext) {
        iterator.next().expr match {
          case Some(e) => clausesTypes += e.`type`().getOrNothing
          case _ =>
        }
      }

      val clausesLubType =
        if (clausesTypes.isEmpty) Nothing
        else                      clausesTypes.lub()

      implicit val resolveScope: GlobalSearchScope = this.resolveScope

      getContext match {
        case _: ScCatchBlock =>
          val manager = ScalaPsiManager.instance
          val funs = manager.getCachedClasses(resolveScope, PartialFunctionType.TypeName)
          val fun = funs.find(_.is[ScTrait]).getOrElse(return Failure(ScalaBundle.message("cannot.find.partialfunction.class")))
          val throwable = manager.getCachedClass(resolveScope, "java.lang.Throwable").orNull
          if (throwable == null) return Failure(ScalaBundle.message("cannot.find.throwable.class"))
          return Right(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesLubType)))
        case _ =>
          val et = this.expectedType(fromUnderscore = false)
            .getOrElse(return Failure(ScalaBundle.message("cannot.infer.type.without.expected.type")))

          return et match {
            case FunctionType(_, params) =>
              Right(FunctionType(clausesLubType, params.map(_.removeVarianceAbstracts())))
            case PartialFunctionType(_, param) =>
              Right(PartialFunctionType(clausesLubType, param.removeVarianceAbstracts()))
            case _ =>
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
      case Some(e) => e.`type`().getOrAny
    }
    Right(inner)
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
