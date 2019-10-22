package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiParameterAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScImportableDeclarationsOwner, ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{FunctionLikeType, ScAbstractType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.annotation.tailrec


/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScParameter extends ScTypedDefinition with ScModifierListOwner
                  with PsiParameterAdapter with ScImportableDeclarationsOwner { self =>
  override def getTypeElement: PsiTypeElement

  def isWildcard: Boolean = "_" == name

  override def isVarArgs: Boolean = isRepeatedParameter

  override def computeConstantValue: Object = null

  override def normalizeDeclaration(): Unit = {}

  override def hasInitializer: Boolean = false

  override def getInitializer: PsiExpression = null

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType] = findChild(classOf[ScParameterType])

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

  def isRepeatedParameter: Boolean

  def isCallByNameParameter: Boolean

  def baseDefaultParam: Boolean

  def getActualDefaultExpression: Option[ScExpression]

  def getRealParameterType: TypeResult =
    `type`() match {
      case Right(tp) if isRepeatedParameter => Right(tp.tryWrapIntoSeqType)
      case f                                => f
    }

  override def getDeclarationScope: ScalaPsiElement = PsiTreeUtil.getContextOfType(this, classOf[ScParameterOwner], classOf[ScFunctionExpr])

  def deprecatedName: Option[String]

  def owner: PsiElement = {
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr],
      classOf[ScFunction], classOf[ScPrimaryConstructor])
  }

  def isImplicitParameter: Boolean = {
    val clause = PsiTreeUtil.getParentOfType(this, classOf[ScParameterClause])
    if (clause == null) return false
    clause.isImplicit
  }

  def index: Int = getParent.getParent match {
    case parameters: ScParameters => parameters.params.indexOf(this)
    case _ => getParent.asInstanceOf[ScParameterClause].parameters.indexOf(this)
  }

  def getType: PsiType = getRealParameterType.getOrNothing.toPsiType

  def isAnonymousParameter: Boolean = getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      case _: ScFunctionExpr => true
      case _ => false
    }
    case _ => false
  }

  /**
   * Infers expected type for the parameter of an anonymous function
   * based on the corresponding function-like type.
   */
  def expectedParamType: Option[ScType] = getContext match {
    case clause: ScParameterClause => clause.getContext.getContext match {
      case fn: ScFunctionExpr =>
        val functionLikeType = FunctionLikeType(this)
        val eTpe             = fn.expectedType(fromUnderscore = false)
        val idx              = clause.parameters.indexOf(this)
        val isUnderscoreFn   = ScUnderScoreSectionUtil.isUnderscoreFunction(fn)

        @tailrec
        def extractFromFunctionType(tpe: ScType, checkDeep: Boolean = false): Option[ScType] =
          tpe match {
            case functionLikeType(_, retTpe, _) if checkDeep => extractFromFunctionType(retTpe)
              case functionLikeType(_, _, paramTpes) =>
                paramTpes.lift(idx).flatMap {
                  case abs: ScAbstractType if abs.upper.isAny => None
                  case tpe                                    => Option(tpe.removeAbstracts)
                }
            case _                                           => None
          }

        val maybeExpectedParamTpe = eTpe.flatMap(extractFromFunctionType(_, isUnderscoreFn))
        maybeExpectedParamTpe.orElse(inferExpectedParamTypeUndoingEtaExpansion(fn))
      case _ => None
    }
  }

  /**
   * When typing a parameter of function literal of shape `(a1, ... aN) => f(a1, ...., aN)`,
   * if we failed to find an expected type from an expected type of a corresponding function literal
   * (e.g. because there were multiple overloaded alternatives for `f` w/o matching parameter types)
   * try inferring it from the usage of [[self]] in the (non-polymorphic) function call.
   */
  private[this] def inferExpectedParamTypeUndoingEtaExpansion(fn: ScFunctionExpr): Option[ScType] =
    fn.result.collect { case ResultOfEtaExpansion(tpe) => tpe }

  private object ResultOfEtaExpansion {
    def unapply(invocation: MethodInvocation): Option[ScType] = {
      val maybeInvokedAndArgs = invocation match {
        case MethodInvocation(inv: ScReferenceExpression, args)          => (inv, args).toOption
        case ScBlock(MethodInvocation(inv: ScReferenceExpression, args)) => (inv, args).toOption
        case _                                                           => None
      }

      for {
        (inv, args)  <- maybeInvokedAndArgs
        targetMethod <- inv.bind().collect { case ScalaResolveResult(m: PsiMethod, _) => m }
        if !targetMethod.hasTypeParameters // if the function is polymorphic -- bail out
        targetArg <- args.find(_.getText == self.name)
        eTpe      <- targetArg.expectedType(false)
      } yield eTpe
    }
  }

  def getTypeNoResolve: PsiType = PsiType.VOID

  @Cached(ModCount.getBlockModificationCount, this)
  def isDefaultParam: Boolean = calcIsDefaultParam(this, Set.empty)


  @tailrec
  private def calcIsDefaultParam(param: ScParameter, visited: Set[ScParameter]): Boolean = {
    if (param.baseDefaultParam) return true
    if (visited.contains(param)) return false
    getSuperParameter match {
      case Some(superParam) =>
        calcIsDefaultParam(superParam, visited + param)
      case _ => false
    }
  }

  def getDefaultExpression: Option[ScExpression] = {
    val res = getActualDefaultExpression
    if (res.isEmpty) {
      getSuperParameter.flatMap(_.getDefaultExpression)
    } else res
  }

  def getDefaultExpressionInSource: Option[ScExpression] = {
    val res = getActualDefaultExpression
    if (res.isEmpty) {
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
