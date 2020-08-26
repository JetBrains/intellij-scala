package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.substitutorCache
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait ParameterizedType extends ValueType {

  override implicit def projectContext: ProjectContext = designator.projectContext

  val designator: ScType
  val typeArguments: collection.Seq[ScType]

  def substitutor: ScSubstitutor =
    substitutorCache.computeIfAbsent(this, _ => substitutorInner)

  protected def substitutorInner: ScSubstitutor

  override def typeDepth: Int = {
    val result = designator.typeDepth
    typeArguments.map(_.typeDepth) match {
      case collection.Seq() => result //todo: shouldn't be possible
      case seq => result.max(seq.max + 1)
    }
  }

  override def isFinalType: Boolean =
    designator.isFinalType && typeArguments.filterByType[TypeParameterType].forall(_.isInvariant)


  //for name-based extractor
  final def isEmpty: Boolean = false
  final def get: ParameterizedType = this
  final def _1: ScType = designator
  final def _2: collection.Seq[ScType] = typeArguments
}

object ParameterizedType {
  val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] =
    new ConcurrentHashMap[ParameterizedType, ScSubstitutor]()

  def apply(designator: ScType, typeArguments: collection.Seq[ScType]): ValueType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  //designator and type arguments
  def unapply(p: ParameterizedType): ParameterizedType = p
}
