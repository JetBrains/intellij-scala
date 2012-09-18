package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import statements.{ScDeclaredElementsHolder, ScTypeAlias}
import toplevel.templates.ScTemplateBody
import base.patterns.{ScCaseClauses, ScCaseClause}
import types.result.{Failure, Success, TypingContext, TypeResult}
import collection.mutable.HashMap
import toplevel.ScTypedDefinition
import com.intellij.psi.util.PsiTreeUtil
import impl.{ScalaPsiManager, ScalaPsiElementFactory}
import types._
import com.intellij.psi.{PsiElement, ResolveState}
import toplevel.typedef._
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode

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
          val et = expectedType(false).getOrElse(return Failure("Cannot infer type without expected type", Some(this)))
          return ScType.extractFunctionType(et) match {
            case Some(f@ScFunctionType(_, params)) =>
              Success(new ScFunctionType(clausesType, params.map(_.removeVarianceAbstracts(1)))(f.getProject, f.getScope),
                Some(this))
            case None =>
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
      case Some(e) => {
        val m = new HashMap[String, ScExistentialArgument]
        def existize(t: ScType): ScType = t match {
          case fun@ScFunctionType(ret, params) => new ScFunctionType(existize(ret),
            collection.immutable.Seq(params.map({existize _}).toSeq: _*))(fun.getProject, fun.getScope)
          case ScTupleType(comps) =>
            new ScTupleType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*))(getProject, getResolveScope)
          case ScDesignatorType(des) if PsiTreeUtil.isContextAncestor(this, des, true) => des match {
            case obj: ScObject => {
              val t = existize(leastClassType(obj))
              m.put(obj.name, new ScExistentialArgument("_", Nil, t, t))
              new ScTypeVariable(obj.name)
            }
            case clazz: ScTypeDefinition => {
              val t = existize(leastClassType(clazz))
              val vars = clazz.typeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}.toList
              m.put(clazz.name, new ScExistentialArgument("_", vars, t, t))
              new ScTypeVariable(clazz.name)
            }
            case typed: ScTypedDefinition => {
              val t = existize(typed.getType(TypingContext.empty).getOrAny)
              m.put(typed.name, new ScExistentialArgument("_", Nil, t, t))
              new ScTypeVariable(typed.name)
            }
            case _ => t
          }
          case ScProjectionType(p, elem, subst, s) => new ScProjectionType(existize(p), elem, subst, s)
          case ScCompoundType(comps, decls, types, s) =>
            new ScCompoundType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*), decls, types, s)
          case JavaArrayType(arg) => JavaArrayType(existize(arg))
          case ScParameterizedType(des, typeArgs) =>
            new ScParameterizedType(existize(des), collection.immutable.Seq(typeArgs.map({existize _}).toSeq: _*))
          case ScExistentialArgument(name, args, lower, upper) =>
            new ScExistentialArgument(name, args, existize(lower), existize(upper))
          case ex@ScExistentialType(q, wildcards) => {
            new ScExistentialType(existize(q), wildcards.map {
              ex =>
                new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound), existize(ex.upperBound))
            })
          }
          case _ => t
        }
        val t = existize(e.getType(TypingContext.empty).getOrAny)
        if (m.size == 0) t else new ScExistentialType(t, m.values.toList)
      }
    }
    Success(inner, Some(this))
  }

  private def leastClassType(t : ScTemplateDefinition): ScType = {
    val (holders, aliases): (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = t.extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) => {
        // jzaugg: Without these type annotations, a class cast exception occured above. I'm not entirely sure why.
        (b.holders: Seq[ScDeclaredElementsHolder], b.aliases: Seq[ScTypeAlias])
      }
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = t.extendsBlock.superTypes
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      new ScCompoundType(superTypes, holders.toList, aliases.toList, ScSubstitutor.empty)
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
}
