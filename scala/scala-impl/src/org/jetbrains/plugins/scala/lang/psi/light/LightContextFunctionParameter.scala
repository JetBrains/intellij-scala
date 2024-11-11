package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.{PsiElement, PsiFile, PsiManager, PsiTypeElement}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.extensions.LockExtensions
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType}

import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * Synthetic parameters of context-function types, used in implicit search.
 */
final case class LightContextFunctionParameter(project: Project, syntheticName: String, rawType: ScType)
  extends LightElement(PsiManager.getInstance(project), Scala3Language.INSTANCE)
    with ScParameter {
  override def getTypeElement: PsiTypeElement                   = null
  override def typeElement: Option[ScTypeElement]               = None
  override def isRepeatedParameter: Boolean                     = false
  override def isCallByNameParameter: Boolean                   = false
  override def baseDefaultParam: Boolean                        = false
  override def getActualDefaultExpression: Option[ScExpression] = None
  override def deprecatedName: Option[String]                   = None
  override def expectedParamType: Option[ScType]                = None
  override def toString: String                                 = syntheticName
  override def nameId: PsiElement                               = null
  override def name: String                                     = syntheticName
  override def isImplicitParameter: Boolean                     = false
  override def isContextParameter: Boolean                      = true
  override def isAnonymousContextParameter: Boolean             = false

  override def getContainingFile: PsiFile =
    DummyHolderFactory.createHolder(PsiManager.getInstance(project), null)

  private val lock = new ReentrantLock()

  override def `type`(): TypeResult = lock.withLock {
    Right(rawType.updateRecursively {
      case abs: ScAbstractType => invariantTypeParameters.getOrElse(abs, UndefinedType(abs.typeParameter))
    })
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    null

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    Array.empty(ClassTag(clazz))

  /**
   * If we encounter type parameter in an invariant position
   * we must eagerly substitute it, so that next non-matching usage
   * will corretly fail to resolve.
   */
  private val invariantTypeParameters: mutable.Map[ScType, ScType] = {
    val tps = new mutable.HashMap[ScType, ScType]

    rawType.recursiveVarianceUpdate() {
      case (abs: ScAbstractType, variance) =>
        if (variance.isInvariant) tps.put(abs, UndefinedType(abs.typeParameter))
        ProcessSubtypes
      case _ => ProcessSubtypes
    }

    tps
  }

  /**
   * Unique types instantiated via usages of this param
   * in implicit resolution.
   */
  private val constraints: mutable.ArrayBuffer[ScType] = mutable.ArrayBuffer.empty

  def updateWithSubst(subst: ScSubstitutor): Unit = lock.withLock {
    if (constraints.isEmpty)
      invariantTypeParameters.mapValuesInPlace { case (tpt, _) => subst(tpt) }

    val newInstantiation = subst(`type`().getOrAny).inferValueType

    if (!constraints.exists(_.equiv(newInstantiation)))
      constraints += newInstantiation
  }

  def contextFunctionParameterType: TypeResult = lock.withLock {
    val result =
      if (constraints.isEmpty) `type`().map(_.inferValueType)
      else                     Right(constraints.reduceLeft(_ glb _))

    result.map(_.recursiveVarianceUpdate() {
      case (tpt: TypeParameterType, variance) =>
        val result =
          if (variance.isCovariant)          tpt.lowerType
          else if (variance.isContravariant) tpt.upperType
          else                               tpt

        ReplaceWith(result)
      case _ => ProcessSubtypes
    })
  }
}
