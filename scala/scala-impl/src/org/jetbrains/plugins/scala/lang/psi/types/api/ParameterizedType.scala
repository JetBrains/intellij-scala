package org.jetbrains.plugins.scala.lang.psi.types.api

import java.util.concurrent.ConcurrentMap

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.substitutorCache
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, ScSubstitutor, Update}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait ParameterizedType extends ValueType {

  override implicit def projectContext: ProjectContext = designator.projectContext

  val designator: ScType
  val typeArguments: Seq[ScType]

  def substitutor: ScSubstitutor = Option(substitutorCache.get(this)).getOrElse {
    val result = substitutorInner
    substitutorCache.put(this, result)
    result
  }

  protected def substitutorInner: ScSubstitutor

  override def removeAbstracts = ParameterizedType(designator.removeAbstracts,
    typeArguments.map(_.removeAbstracts))

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ValueType = {
    ParameterizedType(
      designator.recursiveUpdateImpl(updates, visited),
      typeArguments.map(_.recursiveUpdateImpl(updates, visited))
    )
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType = {

    val argUpdateSign: Variance = variance match {
      case Invariant | Covariant => Covariant.inverse(revertVariances)
      case Contravariant         => Contravariant.inverse(revertVariances)
    }

    val des = designator.extractDesignated(expandAliases = false) match {
      case Some(n: ScTypeParametersOwner) => n.typeParameters.map(_.variance)
      case _                              => Seq.empty
    }
    ParameterizedType(designator.recursiveVarianceUpdate(update, variance),
      typeArguments.zipWithIndex.map {
        case (ta, i) =>
          val v = if (i < des.length) des(i) else Invariant
          ta.recursiveVarianceUpdate(update, v * argUpdateSign)
      })
  }

  override def typeDepth: Int = {
    val result = designator.typeDepth
    typeArguments.map(_.typeDepth) match {
      case Seq() => result //todo: shouldn't be possible
      case seq => result.max(seq.max + 1)
    }
  }

  override def isFinalType: Boolean =
    designator.isFinalType && typeArguments.filterBy[TypeParameterType].forall(_.isInvariant)
}

object ParameterizedType {
  val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] =
    ContainerUtil.createConcurrentWeakMap[ParameterizedType, ScSubstitutor]()

  def apply(designator: ScType, typeArguments: Seq[ScType]): ValueType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  def unapply(parameterized: ParameterizedType): Option[(ScType, Seq[ScType])] =
    Some(parameterized.designator, parameterized.typeArguments)
}
