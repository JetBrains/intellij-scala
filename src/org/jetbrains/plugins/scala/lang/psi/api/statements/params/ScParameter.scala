package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import javax.swing.Icon

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScImportableDeclarationsOwner, ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScParameterizedType, ScType}

import scala.annotation.tailrec
import scala.collection.immutable.HashSet

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScParameter extends ScTypedDefinition with ScModifierListOwner with
        PsiParameter with ScAnnotationsHolder with ScImportableDeclarationsOwner {
  def getTypeElement: PsiTypeElement

  def isWildcard: Boolean = "_" == name

  def isVarArgs = isRepeatedParameter

  def computeConstantValue = null

  def normalizeDeclaration() {}

  def hasInitializer = false

  def getInitializer = null

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType] = findChild(classOf[ScParameterType])

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

  def isRepeatedParameter: Boolean

  def isCallByNameParameter: Boolean

  def baseDefaultParam: Boolean

  def getActualDefaultExpression: Option[ScExpression]

  def getRealParameterType(ctx: TypingContext): TypeResult[ScType] = {
    if (!isRepeatedParameter) return getType(ctx)
    getType(ctx) match {
      case f@Success(tp: ScType, elem) =>
        val seq = ScalaPsiManager.instance(getProject).getCachedClass("scala.collection.Seq", getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
        if (seq != null) {
          Success(ScParameterizedType(ScType.designator(seq), Seq(tp)), elem)
        } else f
      case f => f
    }
  }

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner], classOf[ScFunctionExpr])

  def deprecatedName: Option[String]

  def owner: PsiElement = {
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr],
      classOf[ScFunction], classOf[ScPrimaryConstructor])
  }

  def remove()

  def isImplicitParameter: Boolean = {
    val clause = PsiTreeUtil.getParentOfType(this, classOf[ScParameterClause])
    if (clause == null) return false
    clause.isImplicit
  }

  def index = getParent.asInstanceOf[ScParameterClause].parameters.indexOf(this)

  override def getName: String = {
    val res = super.getName
    if (JavaLexer.isKeyword(res, LanguageLevel.HIGHEST)) "_" + res
    else res
  }

  abstract override def getUseScope = {
    val specificScope = getDeclarationScope match {
      case null => GlobalSearchScope.EMPTY_SCOPE
      case expr: ScFunctionExpr => new LocalSearchScope(expr)
      case clazz: ScClass if clazz.isCase => clazz.getUseScope
      case clazz: ScClass if this.isInstanceOf[ScClassParameter] => clazz.getUseScope //for named parameters
      case d => d.getUseScope
    }
    specificScope.intersectWith(super.getUseScope)
  }

  def getType: PsiType = ScType.toPsi(getRealParameterType(TypingContext.empty).getOrNothing, getProject, getResolveScope)

  def isAnonymousParameter: Boolean = getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      case f: ScFunctionExpr => true
      case _ => false
    }
    case _ => false
  }

  def expectedParamType: Option[ScType] = getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      // For parameter of anonymous functions to infer parameter's type from an appropriate
      // an. fun's type
      case f: ScFunctionExpr =>
        var result: Option[ScType] = null //strange logic to handle problems with detecting type
        for (tp <- f.expectedTypes(fromUnderscore = false) if result != None) {
          @tailrec
          def applyForFunction(tp: ScType, checkDeep: Boolean) {
            tp.removeAbstracts match {
              case ScFunctionType(ret, _) if checkDeep => applyForFunction(ret, checkDeep = false)
              case ScFunctionType(_, params) if params.length == f.parameters.length =>
                val i = clause.parameters.indexOf(this)
                if (result != null) result = None
                else result = Some(params(i))
              case _ =>
            }
          }
          applyForFunction(tp, ScUnderScoreSectionUtil.underscores(f).length > 0)
        }
        if (result == null || result == None) result = None //todo: x => foo(x)
        result
      case _ => None
    }
  }

  def getTypeNoResolve: PsiType = PsiType.VOID

  @volatile
  private var defaultParam: Option[Boolean] = None

  @volatile
  private var defaultParamModCount: Long = 0L

  def isDefaultParam: Boolean = {
    @tailrec
    def check(param: ScParameter, visited: HashSet[ScParameter]): Boolean = {
      if (param.baseDefaultParam) return true
      if (visited.contains(param)) return false
      getSuperParameter match {
        case Some(superParam) =>
          check(superParam, visited + param)
        case _ => false
      }
    }

    val count = getManager.getModificationTracker.getModificationCount
    defaultParam match {
      case Some(res) if count == defaultParamModCount => res
      case _ =>
        val res = check(this, HashSet.empty)
        defaultParamModCount = count
        defaultParam = Some(res)
        res
    }
  }

  def getDefaultExpression: Option[ScExpression] = {
    val res = getActualDefaultExpression
    if (res == None) {
      getSuperParameter.flatMap(_.getDefaultExpression)
    } else res
  }

  def getDefaultExpressionInSource: Option[ScExpression] = {
    val res = getActualDefaultExpression
    if (res == None) {
      getSuperParameter.flatMap(_.getDefaultExpressionInSource)
    } else {
      getContainingFile match {
        case file: ScalaFile =>
          if (file.isCompiled) {
            val containingMember = PsiTreeUtil.getContextOfType(this, true, classOf[ScMember])
            if (containingMember == null) res
            else {
              def extractFromParameterOwner(owner: ScParameterOwner): Option[ScExpression] = {
                owner.parameters.find(_.name == name) match {
                  case Some(param) => param.getDefaultExpression
                  case _ => res
                }
              }
              containingMember match {
                case c: ScClass =>
                  c.getSourceMirrorClass match {
                    case c: ScClass => extractFromParameterOwner(c)
                    case _ => res
                  }
                case f: ScFunction =>
                  f.getNavigationElement match {
                    case f: ScFunction => extractFromParameterOwner(f)
                    case _ => res
                  }
                case _ => res
              }
            }
          } else res
        case _ => res
      }
    }
  }

  def getSuperParameter: Option[ScParameter] = {
    getParent match {
      case clause: ScParameterClause =>
        val i = clause.parameters.indexOf(this)
        clause.getParent match {
          case p: ScParameters =>
            val j = p.clauses.indexOf(clause)
            p.getParent match {
              case fun: ScFunction =>
                fun.superMethod match {
                  case Some(method: ScFunction) =>
                    val clauses: Seq[ScParameterClause] = method.paramClauses.clauses
                    if (j >= clauses.length) return None
                    val parameters: Seq[ScParameter] = clauses.apply(j).parameters
                    if (i >= parameters.length) return None
                    Some(parameters.apply(i))
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }
}