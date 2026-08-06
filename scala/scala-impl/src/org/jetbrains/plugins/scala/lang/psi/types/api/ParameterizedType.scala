package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType.substitutorCache
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

trait ParameterizedType extends ValueType {

  override implicit def projectContext: ProjectContext = designator.projectContext

  val designator: ScType
  val typeArguments: Seq[ScType]

  def substitutor: ScSubstitutor =
    substitutorCache(projectContext.project).computeIfAbsent(this, _ => substitutorInner)

  protected def substitutorInner: ScSubstitutor

  override def typeDepth: Int = {
    val result = designator.typeDepth
    typeArguments.map(_.typeDepth) match {
      case Seq() => result //todo: shouldn't be possible
      case seq => result.max(seq.max + 1)
    }
  }

  override def isFinalType(implicit context: Context): Boolean =
    designator.isFinalType && typeArguments.filterByType[TypeParameterType].forall(_.isInvariant)

  /**
   * For context function types returns synthetic parameters,
   * which are used for implicit resolution inside context function body.
   */
  lazy val contextParameters: Seq[Seq[LightContextFunctionParameter]] = {
    def aux(tp: ScType, fromIdx: Int): Seq[Seq[LightContextFunctionParameter]] = tp match {
      case ContextFunctionType(retTpe, paramTypes) =>
        val outerParams =
          paramTypes.mapWithIndex((tpe, idx) =>
            LightContextFunctionParameter(projectContext.project, s"ev$$${idx + fromIdx}", tpe))

        val innerParams = aux(retTpe, fromIdx + outerParams.size)
        outerParams +: innerParams
      case _ => Seq.empty
    }

    aux(this, 0)
  }
}

object ParameterizedType {

  def substitutorCache(project: Project): ConcurrentMap[ParameterizedType, ScSubstitutor] =
    project.getService(classOf[SubstitutorCacheService]).substitutorCache

  @Service(Array(Service.Level.PROJECT))
  private final class SubstitutorCacheService {
    val substitutorCache: ConcurrentMap[ParameterizedType, ScSubstitutor] = new ConcurrentHashMap()
  }

  def apply(designator: ScType, typeArguments: Seq[ScType]): ScType =
    designator.typeSystem.parameterizedType(designator, typeArguments)

  def unapply(p: ParameterizedType): Option[(ScType, Seq[ScType])] =
    Some((p.designator, p.typeArguments))
}
