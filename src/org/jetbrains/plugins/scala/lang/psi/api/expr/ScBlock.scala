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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}

import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
 * Author: ilyas, alefas
 */

trait ScBlock extends ScExpression with ScDeclarationSequenceHolder with ScImportsHolder {

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    if (hasCaseClauses) {
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
          val throwable = manager.getCachedClass(getResolveScope, "java.lang.Throwable").orNull
          if (throwable == null) return Failure("Cannot find Throwable class", Some(this))
          return Success(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesType)), Some(this))
        case _ =>
          val et = expectedType(fromUnderscore = false).getOrElse(return Failure("Cannot infer type without expected type", Some(this)))
          return et match {
            case f@ScFunctionType(_, params) =>
              Success(ScFunctionType(clausesType, params.map(_.removeVarianceAbstracts(1)))
                (getProject, getResolveScope), Some(this))
            case f@ScPartialFunctionType(_, param) =>
              Success(ScPartialFunctionType(clausesType, param.removeVarianceAbstracts(1))
                (getProject, getResolveScope), Some(this))
            case _ =>
              Failure("Cannot infer type without expected type of scala.FunctionN or scala.PartialFunction", Some(this))
          }
      }
    }
    val inner = lastExpr match {
      case None =>
        ScalaPsiUtil.fileContext(this) match {
          case scalaFile: ScalaFile if scalaFile.isCompiled => Nothing
          case _ => Unit
        }
      case Some(e) =>
        val m = new mutable.HashMap[String, ScExistentialArgument]
        def existize(t: ScType, visited: HashSet[ScType]): ScType = {
          if (visited.contains(t)) return t
          val visitedWithT = visited + t
          t match {
            case ScDesignatorType(p: ScParameter) if p.owner.isInstanceOf[ScFunctionExpr] && p.owner.asInstanceOf[ScFunctionExpr].result == Some(this) =>
              val t = existize(p.getType(TypingContext.empty).getOrAny, visitedWithT)
              m.put(p.name, new ScExistentialArgument(p.name, Nil, t, t))
              new ScTypeVariable(p.name)
            case ScDesignatorType(typed: ScBindingPattern) if typed.nameContext.isInstanceOf[ScCaseClause] &&
              typed.nameContext.asInstanceOf[ScCaseClause].expr == Some(this) =>
              val t = existize(typed.getType(TypingContext.empty).getOrAny, visitedWithT)
              m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
              new ScTypeVariable(typed.name)
            case ScDesignatorType(des) if PsiTreeUtil.isContextAncestor(this, des, true) => des match {
              case obj: ScObject =>
                val t = existize(leastClassType(obj), visitedWithT)
                m.put(obj.name, new ScExistentialArgument(obj.name, Nil, t, t))
                new ScTypeVariable(obj.name)
              case clazz: ScTypeDefinition =>
                val t = existize(leastClassType(clazz), visitedWithT)
                val vars = clazz.typeParameters.map {tp => ScalaPsiManager.typeVariable(tp)}.toList
                m.put(clazz.name, new ScExistentialArgument(clazz.name, vars, t, t))
                new ScTypeVariable(clazz.name)
              case typed: ScTypedDefinition =>
                val t = existize(typed.getType(TypingContext.empty).getOrAny, visitedWithT)
                m.put(typed.name, new ScExistentialArgument(typed.name, Nil, t, t))
                new ScTypeVariable(typed.name)
              case _ => t
            }
            case proj@ScProjectionType(p, elem, s) => ScProjectionType(existize(p, visitedWithT), elem, s)
            case ScCompoundType(comps, signatureMap, typesMap) =>
              new ScCompoundType(comps.map(existize(_, visitedWithT)), signatureMap.map {
                case (s: Signature, tp) =>
                  def updateTypeParam(tp: TypeParameter): TypeParameter = {
                    new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), () => existize(tp.lowerType(), visitedWithT),
                      () => existize(tp.upperType(), visitedWithT), tp.ptp)
                  }

                  val pTypes: List[Seq[() => ScType]] =
                    s.substitutedTypes.map(_.map(f => () => existize(f(), visitedWithT)))
                  val tParams: Array[TypeParameter] = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(updateTypeParam)
                  val rt: ScType = existize(tp, visitedWithT)
                  (new Signature(s.name, pTypes, s.paramLength, tParams,
                    ScSubstitutor.empty, s.namedElement match {
                      case fun: ScFunction =>
                        ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
                      case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                      case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                      case named => named
                    }, s.hasRepeatedParam), rt)
              }, typesMap.map {
                case (s, sign) => (s, sign.updateTypes(existize(_, visitedWithT)))
              })
            case JavaArrayType(arg) => JavaArrayType(existize(arg, visitedWithT))
            case ScParameterizedType(des, typeArgs) =>
              ScParameterizedType(existize(des, visitedWithT), typeArgs.map(existize(_, visitedWithT)))
            case ex@ScExistentialType(q, wildcards) =>
              new ScExistentialType(existize(q, visitedWithT), wildcards.map {
                ex => new ScExistentialArgument(ex.name, ex.args, existize(ex.lowerBound, visitedWithT), existize(ex.upperBound, visitedWithT))
              })
            case _ => t
          }
        }
        val t = existize(e.getType(TypingContext.empty).getOrAny, HashSet.empty)
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
      ScCompoundType.fromPsi(superTypes, holders.toList, aliases.toList)
    } else superTypes(0)
  }

  def hasCaseClauses: Boolean = false
  def isInCatchBlock: Boolean = getContext.isInstanceOf[ScCatchBlock]
  def isAnonymousFunction = hasCaseClauses && !isInCatchBlock

  def exprs: Seq[ScExpression] = findChildrenByClassScala(classOf[ScExpression]).toSeq
  def statements: Seq[ScBlockStatement] = findChildrenByClassScala(classOf[ScBlockStatement]).toSeq
  
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