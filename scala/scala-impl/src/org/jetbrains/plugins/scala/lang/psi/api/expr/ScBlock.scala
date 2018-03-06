package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLineNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Author: ilyas, alefas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  protected override def innerType: TypeResult = {
    if (hasCaseClauses) {
      val caseClauses = findChildByClassScala(classOf[ScCaseClauses])
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
        else clausesTypes.lub(checkWeak = true)

      implicit val resolveScope = this.resolveScope

      getContext match {
        case _: ScCatchBlock =>
          val manager = ScalaPsiManager.instance
          val funs = manager.getCachedClasses(resolveScope, PartialFunctionType.TypeName)
          val fun = funs.find(_.isInstanceOf[ScTrait]).getOrElse(return Failure("Cannot find PartialFunction class"))
          val throwable = manager.getCachedClass(resolveScope, "java.lang.Throwable").orNull
          if (throwable == null) return Failure("Cannot find Throwable class")
          return Right(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesLubType)))
        case _ =>
          val et = this.expectedType(fromUnderscore = false)
            .getOrElse(return Failure("Cannot infer type without expected type"))

          return et match {
            case FunctionType(_, params) =>
              Right(FunctionType(clausesLubType, params.map(_.removeVarianceAbstracts())))
            case PartialFunctionType(_, param) =>
              Right(PartialFunctionType(clausesLubType, param.removeVarianceAbstracts()))
            case _ =>
              Failure("Cannot infer type without expected type of scala.FunctionN or scala.PartialFunction")
          }
      }
    }
    val inner = lastExpr match {
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
  def isInCatchBlock: Boolean = getContext.isInstanceOf[ScCatchBlock]
  def isAnonymousFunction: Boolean = hasCaseClauses && !isInCatchBlock

  def exprs: Seq[ScExpression] = findChildrenByClassScala(classOf[ScExpression]).toSeq
  def statements: Seq[ScBlockStatement] = findChildrenByClassScala(classOf[ScBlockStatement]).toSeq
  
  def hasRBrace: Boolean = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE)).length == 1
  
  def getRBrace: Option[ASTNode] = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE)) match {
    case Array(node) => Some(node)
    case _ => None
  }

  def lastExpr: Option[ScExpression] = findLastChild(classOf[ScExpression])
  def lastStatement: Option[ScBlockStatement] = findLastChild(classOf[ScBlockStatement])

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
    super[ScImportsHolder].processDeclarations(processor, state, lastParent, place)
  
  def needCheckExpectedType = true
}

object ScBlock {
  def unapplySeq(block: ScBlock): Option[Seq[ScBlockStatement]] = Option(block.statements)
}
