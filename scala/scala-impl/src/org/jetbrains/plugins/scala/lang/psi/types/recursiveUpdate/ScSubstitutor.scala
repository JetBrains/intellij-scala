package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamId}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

import scala.collection.Seq
import scala.collection.immutable.LongMap
import scala.collection.mutable.ArrayBuffer

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
final class ScSubstitutor private(private val substitutions: Array[Substitution]) {
  //Array is used for the best concatenation performance, it is effectively immutable
  //todo: replace with ImmutableArray wrapper after migrating to scala 2.13

  def subst(t: ScType): ScType = {
    if (ScSubstitutor.cacheSubstitutions)
      ScSubstitutor.cache ++= this.allTypeParamsMap

    val view = substitutions.view //has more efficient `.tail` operation
    t.recursiveUpdateImpl(view)
  }

  def followed(other: ScSubstitutor): ScSubstitutor = {
    if (this.isEmpty) other
    else if (other.isEmpty) this
    else {
      val newLength = substitutions.length + other.substitutions.length
      if (newLength > ScSubstitutor.followLimit)
        throw new RuntimeException("Too much followers for substitutor: " + this.toString)

      new ScSubstitutor(substitutions ++ other.substitutions)
    }
  }

  def followUpdateThisType(tp: ScType): ScSubstitutor = {
    tp match {
      case ScThisType(template) =>
        val buffer = ArrayBuffer.empty[Substitution]
        template.withContexts.foreach {
          case t: ScTemplateDefinition =>
            buffer += ThisTypeSubstitution(ScThisType(t))
          case _ => //do nothing
        }
        new ScSubstitutor(buffer.toArray ++ substitutions)
      case _ =>
        ScSubstitutor(tp).followed(this)
    }
  }

  def withBindings(from: Seq[TypeParameter], target: Seq[TypeParameter]): ScSubstitutor = {
    def simple: ScSubstitutor = ScSubstitutor.bind(from, target)(TypeParameterType(_))

    def mergeHead(old: TypeParamSubstitution): ScSubstitutor = {
      val newMap = TypeParamSubstitution.buildMap(from, target, old.tvMap)(TypeParameterType(_))
      val cloned = substitutions.clone()
      cloned(0) = TypeParamSubstitution(newMap)
      new ScSubstitutor(cloned)
    }

    if (from.isEmpty || target.isEmpty) this
    else if (this.isEmpty) simple
    else {
      substitutions.head match {
        case tps: TypeParamSubstitution => mergeHead(tps)
        case _ => simple.followed(this)
      }
    }
  }

  override def hashCode(): Int = substitutions.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: ScSubstitutor => other.substitutions sameElements substitutions
    case _ => false
  }

  override def toString: String = {
    val text = substitutions.mkString(" >> ")
    s"ScSubstitutor($text)"
  }

  def isEmpty: Boolean = substitutions.isEmpty

  private def allTypeParamsMap: LongMap[ScType] = substitutions.foldLeft(LongMap.empty[ScType]) { (map, substitution) =>
    substitution match {
      case TypeParamSubstitution(tvMap) => map ++ tvMap
      case _ => map
    }
  }
}

object ScSubstitutor {
  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  val empty: ScSubstitutor = new ScSubstitutor(Array.empty)

  private def apply(s: Substitution) = new ScSubstitutor(Array(s))

  private val followLimit = 800

  var cacheSubstitutions = false

  var cache: LongMap[ScType] = LongMap.empty

  def apply(tvMap: LongMap[ScType]): ScSubstitutor =
    ScSubstitutor(TypeParamSubstitution(tvMap))

  def apply(updateThisType: ScType): ScSubstitutor =
    ScSubstitutor(ThisTypeSubstitution(updateThisType))

  def paramToExprType(parameters: Seq[Parameter], expressions: Seq[Expression], useExpected: Boolean = true) =
    ScSubstitutor(ParamsToExprs(parameters, expressions, useExpected))

  def paramToParam(fromParams: Seq[ScParameter], toParams: Seq[ScParameter]) =
    ScSubstitutor(ParamToParam(fromParams, toParams))

  def paramToType(fromParams: Seq[Parameter], types: Seq[ScType]) =
    ScSubstitutor(ParamToType(fromParams, types))

  def bind[T: TypeParamId](typeParamsLike: Seq[T])(toScType: T => ScType): ScSubstitutor = {
    val tvMap = TypeParamSubstitution.buildMap(typeParamsLike, typeParamsLike)(toScType)
    ScSubstitutor(tvMap)
  }

  def bind[T: TypeParamId, S](typeParamsLike: Seq[T], targets: Seq[S])(toScType: S => ScType): ScSubstitutor = {
    val tvMap = TypeParamSubstitution.buildMap(typeParamsLike, targets)(toScType)
    ScSubstitutor(tvMap)
  }

  def bind[T: TypeParamId](typeParamsLike: Seq[T], types: Seq[ScType]): ScSubstitutor = {
    val tvMap = TypeParamSubstitution.buildMap(typeParamsLike, types)(identity)
    ScSubstitutor(tvMap)
  }

  def updateThisTypeDeep(subst: ScSubstitutor): Option[ScType] = {
    subst.substitutions.collectFirst {
      case ThisTypeSubstitution(target) => target
    }
  }
}