package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import statements.{ScDeclaredElementsHolder, ScTypeAlias}
import toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClauses, ScCaseClause}
import types.result.{Failure, Success, TypingContext, TypeResult}
import toplevel.ScTypedDefinition
import com.intellij.psi.util.PsiTreeUtil
import impl.{ScalaPsiManager, ScalaPsiElementFactory}
import types._
import com.intellij.psi.{PsiElement, ResolveState}
import toplevel.typedef._
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import scala.collection.mutable

/**
 * Author: ilyas, alefas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    if (isAnonymousFunction) {
      val caseClauses = findChildByClassScala(classOf[ScCaseClauses])
      val clauses: Seq[ScCaseClause] = caseClauses.caseClauses
      val clausesType = clauses.foldLeft(types.Nothing: ScType)((tp, clause) => Bounds.lub(tp, clause.expr match {
        case Some(expr) => expr.getType(TypingContext.empty).getOrNothing
        case _ => types.Nothing
      }))

      getContext match {
        case c: ScCatchBlock =>
          val manager = ScalaPsiManager.instance(getProject)
          val funs = manager.getCachedClasses(getResolveScope, "scala.PartialFunction")
          val fun = funs.find(_.isInstanceOf[ScTrait]).getOrElse(return Failure("Cannot find PartialFunction class", Some(this)))
          val throwable = manager.getCachedClass(getResolveScope, "java.lang.Throwable")
          if (throwable == null) return Failure("Cannot find Throwable class", Some(this))
          return Success(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesType)), Some(this))
        case _ =>
          val et = expectedType(fromUnderscore = false).getOrElse(return Failure("Cannot infer type without expected type", Some(this)))
          return et match {
            case f@ScFunctionType(_, params) =>
              Success(ScFunctionType(clausesType, params.map(_.removeVarianceAbstracts(1)))(getProject, getResolveScope), Some(this))
            case _ =>
              ScType.extractPartialFunctionType(et) match {
                case Some((des, param, _)) =>
                  Success(ScParameterizedType(des, Seq(param.removeVarianceAbstracts(1), clausesType)), Some(this))
                case None =>
                  Failure("Cannot infer type without expected type of scala.FunctionN or scala.PartialFunction", Some(this))
              }
          }
      }
    }
    val inner = lastExpr match {
      case None => Unit
      case Some(e) =>
        val m = new mutable.HashMap[String, ScExistentialArgument]
        def existize(t: ScType): ScType = t match {
          case ScDesignatorType(p: ScParameter) if p.owner.isInstanceOf[ScFunctionExpr] && p.owner.asInstanceOf[ScFunctionExpr].result == Some(this) =>
            val t = existize(p.getType(TypingContext.empty).getOrAny)
            m.put(p.name, new ScExistentialArgument(p.name, Nil, t, t))
            new ScTypeVariable(p.name)
          case ScDesignatorType(typed: ScBindingPattern) if typed.nameContext.isInstanceOf[ScCaseClause] &&
            typed.nameContext.asInstanceOf[ScCaseClause].expr == Some(this) =>
            val t = existize(typed.getType(TypingContext.empty).getOrAny)
            m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
            new ScTypeVariable(typed.name)
          case ScDesignatorType(des) if PsiTreeUtil.isContextAncestor(this, des, true) => des match {
            case obj: ScObject =>
              val t = existize(leastClassType(obj))
              m.put(obj.name, new ScExistentialArgument(obj.name, Nil, t, t))
              new ScTypeVariable(obj.name)
            case clazz: ScTypeDefinition =>
              val t = existize(leastClassType(clazz))
              val vars = clazz.typeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}.toList
              m.put(clazz.name, new ScExistentialArgument(clazz.name, vars, t, t))
              new ScTypeVariable(clazz.name)
            case typed: ScTypedDefinition =>
              val t = existize(typed.getType(TypingContext.empty).getOrAny)
              m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
              new ScTypeVariable(typed.name)
            case _ => t
          }
          case proj@ScProjectionType(p, elem, s) => new ScProjectionType(existize(p), elem, s)
          case ScCompoundType(comps, signatureMap, typesMap) =>
            new ScCompoundType(comps.map(existize), signatureMap.map {
              case (signature: Signature, tp) => (signature, existize(tp))
            }, typesMap.map {
              case (s, (tp1, tp2, ta)) => (s, (existize(tp1), existize(tp2), ta))
            })
          case JavaArrayType(arg) => JavaArrayType(existize(arg))
          case ScParameterizedType(des, typeArgs) =>
            new ScParameterizedType(existize(des), typeArgs.map(existize))
          case ex@ScExistentialType(q, wildcards) =>
            new ScExistentialType(existize(q), wildcards.map {
              ex => new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound), existize(ex.upperBound))
            })
          case _ => t
        }
        val t = existize(e.getType(TypingContext.empty).getOrAny)
        if (m.size == 0) t else new ScExistentialType(t, m.values.toList).simplify()
    }
    Success(inner, Some(this))
  }

  private def leastClassType(t : ScTemplateDefinition): ScType = {
    val (holders, aliases): (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = t.extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) =>
        // jzaugg: Without these type annotations, a class cast exception occured above. I'm not entirely sure why.
        (b.holders: Seq[ScDeclaredElementsHolder], b.aliases: Seq[ScTypeAlias])
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = t.extendsBlock.superTypes
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      ScCompoundType.fromPsi(superTypes, holders.toList, aliases.toList, ScSubstitutor.empty)
    } else superTypes(0)
  }

  def isAnonymousFunction: Boolean = false

  def exprs: Seq[ScExpression] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScExpression]).toSeq: _*)
  def statements: Seq[ScBlockStatement] =
    collection.immutable.Seq(findChildrenByClassScala(classOf[ScBlockStatement]).toSeq: _*)
  
  def hasRBrace: Boolean = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE)).length == 1
  
  def getRBrace: Option[ASTNode] = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE)) match {
    case Array(node) => Some(node)
    case _ => None
  }

  def lastExpr = findLastChild(classOf[ScExpression])
  def lastStatement = findLastChild(classOf[ScBlockStatement])

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
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