package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.scala.collection.mutable.HashMap
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import api.toplevel.{ScTypedDefinition}
import com.intellij.psi.util.PsiTreeUtil
import types._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import api.toplevel.templates.ScTemplateBody
import api.statements.{ScDeclaredElementsHolder, ScTypeAlias}
import collection.Seq
import result.{TypeResult, Failure, Success, TypingContext}
import scala.Some
import com.intellij.psi.PsiClass
import controlFlow.impl.ScalaControlFlowBuilder
import controlFlow.Instruction
import api.base.patterns.{ScCaseClause, ScCaseClauses}

/**
* @author ilyas
*/

class ScBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScBlock {

  override def toString: String = "BlockOfExpressions"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    if (isAnonymousFunction) {
      val caseClauses = findChildByClassScala(classOf[ScCaseClauses])
      val clauses: Seq[ScCaseClause] = caseClauses.caseClauses
      lazy val clausesType = clauses.foldLeft(types.Nothing: ScType)((tp, clause) => Bounds.lub(tp, clause.expr match {
        case Some(expr) => expr.getType(TypingContext.empty).getOrElse(types.Nothing)
        case _ => types.Nothing
      }))
      expectedType match {
        case Some(f@ScFunctionType(retType, params)) => {
          return Success(new ScFunctionType(clausesType, params, f.getProject), Some(this))
        }
        case Some(tp@ScParameterizedType(des, typeArgs)) => {
          ScType.extractClass(tp) match {
            case Some(clazz) if clazz.getQualifiedName.startsWith("scala.Function") => {
              return Success(new ScFunctionType(clausesType, typeArgs.slice(0, typeArgs.length - 1), clazz.getProject), Some(this))
            }
            case Some(clazz) if clazz.getQualifiedName == "scala.PartialFunction" => {
              return Success(ScParameterizedType(des, typeArgs.slice(0, typeArgs.length - 1) ++ Seq(clausesType)), Some(this))
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
          case fun@ScFunctionType(ret, params) => new ScFunctionType(existize(ret), collection.immutable.Seq(params.map({existize _}).toSeq: _*), fun.getProject)
          case ScTupleType(comps) => new ScTupleType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*), getProject)
          case ScDesignatorType(des) if PsiTreeUtil.isAncestor(this, des, true) => des match {
            case clazz: ScClass => {
              val t = existize(leastClassType(clazz))
              val vars = clazz.typeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}.toList
              m.put(clazz.name, new ScExistentialArgument(clazz.name, vars, t, t))
              new ScTypeVariable(clazz.name)
            }
            case obj: ScObject => {
              val t = existize(leastClassType(obj))
              m.put(obj.name, new ScExistentialArgument(obj.name, Nil, t, t))
              new ScTypeVariable(obj.name)
            }
            case typed: ScTypedDefinition => {
              val t = existize(typed.getType(TypingContext.empty).getOrElse(Any))
              m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
              new ScTypeVariable(typed.name)
            }
          }
          case ScProjectionType(p, ref) => new ScProjectionType(existize(p), ref)
          case ScCompoundType(comps, decls, types, s) =>
            new ScCompoundType(collection.immutable.Seq(comps.map({existize _}).toSeq: _*), decls, types, s)
          case ScParameterizedType(des, typeArgs) =>
            new ScParameterizedType(existize(des), collection.immutable.Seq(typeArgs.map({existize _}).toSeq: _*))
          case ScExistentialArgument(name, args, lower, upper) => new ScExistentialArgument(name, args, existize(lower), existize(upper))
          case ex@ScExistentialType(q, wildcards) => {
            new ScExistentialType(existize(q), wildcards.map {
              ex =>
                new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound), existize(ex.upperBound))
            })
          }
          case singl: ScSingletonType => existize(singl.pathType)
          case _ => t
        }
        val t = existize(e.getType(TypingContext.empty).getOrElse(Any))
        if (m.size == 0) t else new ScExistentialType(t, m.values.toList)
      }
    }
    Success(inner, Some(this))
  }

  private def leastClassType(t : ScTypeDefinition) = {
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
}