package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.substitutorCache
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

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
  lazy val contextParameters: Seq[LightContextFunctionParameter] = this match {
    case ContextFunctionType(_, paramTypes) =>
      paramTypes.mapWithIndex((tpe, idx) =>
        LightContextFunctionParameter(projectContext.project, s"ev$$$idx", tpe))
    case _ => Seq.empty
  }
}

object ParameterizedType {
  val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] =
    new ConcurrentHashMap[ParameterizedType, ScSubstitutor]()

  def apply(designator: ScType, typeArguments: Seq[ScType]): ScType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  def unapply(p: ParameterizedType): Option[(ScType, Seq[ScType])] =
    Some((p.designator, p.typeArguments))
}
