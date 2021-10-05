package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.{PsiElement, PsiManager, PsiTypeElement}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.{LightContextFunctionParameter, substitutorCache}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.findUsages.compilerReferences.LockExt

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.reflect.ClassTag

/**
  * @author adkozlov
  */
trait ParameterizedType extends ValueType {

  override implicit def projectContext: ProjectContext = designator.projectContext

  val designator: ScType
  val typeArguments: Seq[ScType]

  def substitutor: ScSubstitutor =
    substitutorCache.computeIfAbsent(this, _ => substitutorInner)

  protected def substitutorInner: ScSubstitutor

  override def typeDepth: Int = {
    val result = designator.typeDepth
    typeArguments.map(_.typeDepth) match {
      case Seq() => result //todo: shouldn't be possible
      case seq => result.max(seq.max + 1)
    }
  }

  override def isFinalType: Boolean =
    designator.isFinalType && typeArguments.filterByType[TypeParameterType].forall(_.isInvariant)

  /**
   * For context function types returns synthetic parameters,
   * which are used for implicit resolution inside context function body.
   */
  val contextParameters: Seq[LightContextFunctionParameter] = this match {
    case ContextFunctionType(_, paramTypes) =>
      paramTypes.mapWithIndex((tpe, idx) =>
        LightContextFunctionParameter(projectContext.project, s"ev$$$idx", tpe))
    case _ => Seq.empty
  }

  //for name-based extractor
  final def isEmpty: Boolean = false
  final def get: ParameterizedType = this
  final def _1: ScType = designator
  final def _2: Seq[ScType] = typeArguments
}

object ParameterizedType {
  val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] =
    new ConcurrentHashMap[ParameterizedType, ScSubstitutor]()

  def apply(designator: ScType, typeArguments: Seq[ScType]): ValueType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  //designator and type arguments
  def unapply(p: ParameterizedType): ParameterizedType = p

  final case class LightContextFunctionParameter(project: Project, syntheticName: String, rawType: ScType)
      extends LightElement(PsiManager.getInstance(project), Scala3Language.INSTANCE)
      with ScParameter {
    import scala.collection.mutable

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
    override def isContextParameter: Boolean                      = true

    private val lock = new ReentrantLock()

    override def `type`(): TypeResult = lock.locked {
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

    def updateWithSubst(subst: ScSubstitutor): Unit = lock.locked {
      if (constraints.isEmpty)
        invariantTypeParameters.mapValuesInPlace { case (tpt, _) => subst(tpt) }

      val newInstantiation = subst(`type`().getOrAny)
      if (!constraints.exists(_.equiv(newInstantiation)))
        constraints += newInstantiation
    }

    def contextFunctionParameterType: TypeResult = lock.locked {
      if (constraints.isEmpty) `type`().map(_.inferValueType)
      else                     Right(constraints.reduceLeft(_ glb _))
    }
  }
}
