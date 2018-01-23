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
          val funs = manager.getCachedClasses(resolveScope, "scala.PartialFunction")
          val fun = funs.find(_.isInstanceOf[ScTrait]).getOrElse(return Failure("Cannot find PartialFunction class"))
          val throwable = manager.getCachedClass(resolveScope, "java.lang.Throwable").orNull
          if (throwable == null) return Failure("Cannot find Throwable class")
          return Right(ScParameterizedType(ScDesignatorType(fun), Seq(ScDesignatorType(throwable), clausesLubType)))
        case _ =>
          val et = this.expectedType(fromUnderscore = false)
            .getOrElse(return Failure("Cannot infer type without expected type"))

          def removeVarianceAbstracts(scType: ScType) = {
            var index = 0
            scType.recursiveVarianceUpdate((tp: ScType, v: Variance) => {
              tp match {
                case ScAbstractType(_, lower, upper) =>
                  v match {
                    case Contravariant => (true, lower)
                    case Covariant     => (true, upper)
                    case Invariant     => (true, ScExistentialArgument(s"_$$${index += 1; index}", Nil, lower, upper))
                  }
                case _ => (false, tp)
              }
            }, Covariant).unpackedType
          }

          return et match {
            case FunctionType(_, params) =>
              Right(FunctionType(clausesLubType, params.map(removeVarianceAbstracts)))
            case PartialFunctionType(_, param) =>
              Right(PartialFunctionType(clausesLubType, removeVarianceAbstracts(param)))
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
      case Some(e) =>
        val m = new mutable.HashMap[String, ScExistentialArgument]
        def existize(t: ScType, visited: Set[ScType]): ScType = {
          if (visited.contains(t)) return t
          val visitedWithT = visited + t
          t match {
            case ScDesignatorType(p: ScParameter) if p.owner.isInstanceOf[ScFunctionExpr] && p.owner.asInstanceOf[ScFunctionExpr].result.contains(this) =>
              val t = existize(p.`type`().getOrAny, visitedWithT)
              val ex = ScExistentialArgument(p.name, Nil, t, t)
              m.put(p.name, ex)
              ex
            case ScDesignatorType(typed: ScBindingPattern) if typed.nameContext.isInstanceOf[ScCaseClause] &&
              typed.nameContext.asInstanceOf[ScCaseClause].expr.contains(this) =>
              val t = existize(typed.`type`().getOrAny, visitedWithT)
              val ex = ScExistentialArgument(typed.name, Nil, t, t)
              m.put(typed.name, ex)
              ex
            case ScDesignatorType(des) if PsiTreeUtil.isContextAncestor(this, des, true) => des match {
              case obj: ScObject =>
                val t = existize(leastClassType(obj), visitedWithT)
                val ex = ScExistentialArgument(obj.name, Nil, t, t)
                m.put(obj.name, ex)
                ex
              case clazz: ScTypeDefinition =>
                val t = existize(leastClassType(clazz), visitedWithT)
                val vars = clazz.typeParameters.map(TypeParameterType(_)).toList
                val ex = ScExistentialArgument(clazz.name, vars, t, t)
                m.put(clazz.name, ex)
                ex
              case typed: ScTypedDefinition =>
                val t = existize(typed.`type`().getOrAny, visitedWithT)
                val ex = ScExistentialArgument(typed.name, Nil, t, t)
                m.put(typed.name, ex)
                ex
              case _ => t
            }
            case ScProjectionType(p, elem, s) => ScProjectionType(existize(p, visitedWithT), elem, s)
            case ScCompoundType(comps, signatureMap, typesMap) =>
              new ScCompoundType(comps.map(existize(_, visitedWithT)), signatureMap.map {
                case (s: Signature, tp) =>
                  def updateTypeParam: TypeParameter => TypeParameter = {
                    case TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
                      TypeParameter(typeParameters.map(updateTypeParam),
                        existize(lowerType, visitedWithT),
                        existize(upperType, visitedWithT),
                        psiTypeParameter)
                  }

                  val pTypes: Seq[Seq[() => ScType]] =
                    s.substitutedTypes.map(_.map(f => () => existize(f(), visitedWithT)))
                  val tParams = s.typeParams.subst(updateTypeParam)
                  val rt: ScType = existize(tp, visitedWithT)
                  (new Signature(s.name, pTypes, tParams,
                    ScSubstitutor.empty, s.namedElement match {
                      case fun: ScFunction =>
                        ScFunction.getCompoundCopy(pTypes.map(_.map(_())), tParams.toList, rt, fun)
                      case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                      case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                      case named => named
                    }, s.hasRepeatedParam), rt)
              }, typesMap.map {
                case (s, sign) => (s, sign.updateTypes(existize(_, visitedWithT)))
              })
            case JavaArrayType(argument) => JavaArrayType(existize(argument, visitedWithT))
            case ParameterizedType(des, typeArgs) =>
              ScParameterizedType(existize(des, visitedWithT), typeArgs.map(existize(_, visitedWithT)))
            case ScExistentialType(q, wildcards) =>
              new ScExistentialType(existize(q, visitedWithT), wildcards.map {
                ex => ScExistentialArgument(ex.name, ex.args, existize(ex.lower, visitedWithT), existize(ex.upper, visitedWithT))
              })
            case _ => t
          }
        }

        val t = existize(e.`type`().getOrAny, Set.empty)
        if (m.isEmpty) t else new ScExistentialType(t, m.values.toList).simplify()
    }
    Right(inner)
  }

  private def leastClassType(t : ScTemplateDefinition): ScType = {
    val (holders, aliases): (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = t.extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) =>
        // jzaugg: Without these type annotations, a class cast exception occured above. I'm not entirely sure why.
        (b.holders: Seq[ScDeclaredElementsHolder], b.aliases: Seq[ScTypeAlias])
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = t.extendsBlock.superTypes
    if (superTypes.length > 1 || holders.nonEmpty || aliases.nonEmpty) {
      ScCompoundType.fromPsi(superTypes, holders.toList, aliases.toList)
    } else superTypes.head
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
