package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiParameterAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGivenDefinition, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScImportableDeclarationsOwner, ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants
import org.jetbrains.plugins.scala.lang.psi.types.result._

import javax.swing.Icon
import scala.annotation.tailrec

trait ScParameter extends ScTypedDefinition
  with ScModifierListOwner
  with PsiParameterAdapter
  with ScImportableDeclarationsOwner { self =>

  override def getTypeElement: PsiTypeElement

  def isWildcard: Boolean = "_" == name

  override def isVarArgs: Boolean = isRepeatedParameter

  override def computeConstantValue: Object = null

  override def normalizeDeclaration(): Unit = {}

  override def hasInitializer: Boolean = false

  override def getInitializer: PsiExpression = null

  def typeElement: Option[ScTypeElement]

  def paramType: Option[ScParameterType] = findChild[ScParameterType]

  override def getIcon(flags: Int): Icon = Icons.PARAMETER

  def isRepeatedParameter: Boolean

  def isCallByNameParameter: Boolean

  // TODO hasDeclaredDefaultArgument
  def baseDefaultParam: Boolean

  def getActualDefaultExpression: Option[ScExpression]

  def getRealParameterType: TypeResult =
    `type`() match {
      case Right(tp) if isRepeatedParameter => Right(tp.tryWrapIntoSeqType)
      case f                                => f
    }

  override def getDeclarationScope: ScalaPsiElement = {
    val ctx = PsiTreeUtil.getContextOfType(this, classOf[ScParameterOwner], classOf[ScFunctionExpr])

    ctx match {
      case cons: ScPrimaryConstructor => cons.containingClass
      case other                      => other
    }
  }

  def deprecatedName: Option[String]

  def owner: PsiElement =
    PsiTreeUtil.getContextOfType(
      this,
      true,
      classOf[ScFunctionExpr],
      classOf[ScFunction],
      classOf[ScPrimaryConstructor],
      classOf[ScExtension],
      classOf[ScGivenDefinition]
    )

  //TODO: Review all usages of `isImplicitParameter` and replace with `isImplicitOrContextParameter` if needed
  // This is basically the same comment as for `ScParameterClause.isImplicit`
  def isImplicitParameter: Boolean

  def isContextParameter: Boolean

  def isImplicitOrContextParameter: Boolean = isImplicitParameter || isContextParameter

  //TODO: rename Anonimous -> Anonymous
  /**
   * @return true - for `String` in `def foo(using String): Unit = ()`<br>
   *         false - for `p: String` in `def foo(p: String): Unit = ()`
   */
  def isAnonimousContextParameter: Boolean

  def index: Int = getParent.getParent match {
    case parameters: ScParameters => parameters.params.indexOf(this)
    case _ => getParent.asInstanceOf[ScParameterClause].parameters.indexOf(this)
  }

  override def getType: PsiType = getRealParameterType.getOrNothing.toPsiType

  /**
   * Infers expected type for the parameter of an anonymous function
   * based on the corresponding function-like type.
   */
  def expectedParamType: Option[ScType]

  def getTypeNoResolve: PsiType = PsiTypeConstants.Void

  // TODO hasDefaultArgument
  def isDefaultParam: Boolean = _isDefaultParam()

  private val _isDefaultParam = cached("isDefaultParam", BlockModificationTracker(this), () => {
    calcIsDefaultParam(this, Set.empty)
  })


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
                    val clauses = method.paramClauses.clauses
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
