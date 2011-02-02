package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import statements.{ScDeclaredElementsHolder, ScTypeAlias}
import toplevel.templates.ScTemplateBody
import toplevel.typedef.{ScObject, ScTypeDefinition, ScTemplateDefinition, ScMember}
import types._
import base.patterns.{ScCaseClauses, ScCaseClause}
import result.{Failure, Success, TypingContext, TypeResult}
import collection.mutable.HashMap
import toplevel.ScTypedDefinition
import com.intellij.psi.util.PsiTreeUtil
import impl.{ScalaPsiManager, ScalaPsiElementFactory}

/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    if (isAnonymousFunction) {
      val caseClauses = findChildByClassScala(classOf[ScCaseClauses])
      val clauses: Seq[ScCaseClause] = caseClauses.caseClauses
      val clausesType = clauses.foldLeft(types.Nothing: ScType)((tp, clause) => Bounds.lub(tp, clause.expr match {
        case Some(expr) => expr.getType(TypingContext.empty).getOrElse(types.Nothing)
        case _ => types.Nothing
      }))
      expectedType match {
        case Some(f@ScFunctionType(retType, params)) => {
          return Success(new ScFunctionType(clausesType, params.map(_.removeAbstracts),
            f.getProject, f.getScope), Some(this))
        }
        case Some(tp@ScParameterizedType(des, typeArgs)) => {
          ScType.extractClass(tp) match {
            case Some(clazz) if clazz.getQualifiedName.startsWith("scala.Function") => {
              return Success(new ScFunctionType(clausesType, typeArgs.slice(0, typeArgs.length - 1).map(_.removeAbstracts),
                clazz.getProject, clazz.getResolveScope), Some(this))
            }
            case Some(clazz) if clazz.getQualifiedName == "scala.PartialFunction" => {
              return Success(ScParameterizedType(des, typeArgs.slice(0, typeArgs.length - 1).map(_.removeAbstracts) ++
                Seq(clausesType)), Some(this))
            }
            case _ => return Failure("Cannot infer type without expected type", Some(this))
          }
        }
        case _ => return Failure("Cannot infer type without expected type", Some(this))
      }
    }
    val inner = lastExpr match {
      case None => Unit
      case Some(e) => {
        val m = new HashMap[String, ScExistentialArgument]
        def existize(t: ScType): ScType = t match {
          case fun@ScFunctionType(ret, params) => new ScFunctionType(existize(ret),
            collection.immutable.Seq(params.map({existize _}).toSeq: _*), fun.getProject, fun.getScope)
          case ScTupleType(comps) => new ScTupleType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*), getProject, getResolveScope)
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
              val t = existize(typed.getType(TypingContext.empty).getOrElse(Any))
              m.put(typed.name, new ScExistentialArgument("_", Nil, t, t))
              new ScTypeVariable(typed.name)
            }
            case _ => t
          }
          case ScProjectionType(p, elem, subst) => new ScProjectionType(existize(p), elem, subst)
          case ScCompoundType(comps, decls, types, s) =>
            new ScCompoundType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*), decls, types, s)
          case JavaArrayType(arg) => JavaArrayType(existize(arg))
          case ScParameterizedType(des, typeArgs) =>
            new ScParameterizedType(existize(des), collection.immutable.Seq(typeArgs.map({existize _}).toSeq: _*))
          case ScExistentialArgument(name, args, lower, upper) => new ScExistentialArgument(name, args, existize(lower), existize(upper))
          case ex@ScExistentialType(q, wildcards) => {
            new ScExistentialType(existize(q), wildcards.map {
              ex =>
                new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound), existize(ex.upperBound))
            })
          }
          case _ => t
        }
        val t = existize(e.getType(TypingContext.empty).getOrElse(Any))
        if (m.size == 0) t else new ScExistentialType(t, m.values.toList)
      }
    }
    Success(inner, Some(this))
  }

  private def leastClassType(t : ScTemplateDefinition) = {
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

  def lastExpr = findLastChild(classOf[ScExpression])
  def lastStatement = findLastChild(classOf[ScBlockStatement])

  def addDefinition(decl: ScMember, before: PsiElement): Boolean = {
    getNode.addChild(decl.getNode,before.getNode)
    getNode.addChild(ScalaPsiElementFactory.createNewLineNode(getManager), before.getNode)
    return true
  }

  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean =
    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
    super[ScImportsHolder].processDeclarations(processor, state, lastParent, place)
}
