package org.jetbrains.plugins.scala.lang.psi
package types
package recursiveUpdate

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.extensions.ArrayExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamId}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, TypeParameter, TypeParameterType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.immutable.LongMap
import scala.util.hashing.MurmurHash3

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */

/** [[ScSubstitutor]] is a transformation of a type which is applied recursively from top to leaves.
  * Examples of such transformation:
  * - replacing type parameter with it's bound or actual argument
  * - replacing `this` type to a concrete inheritor type
  *
  * It's also possible to chain several substitutors to create a compound one.
  *
  * If it's known that every substitution in a chain may update only leaf subtypes, than we may avoid
  * recursively traversing a type several times.
  **/
final class ScSubstitutor private(_substitutions: Array[Update],   //Array is used for the best concatenation performance, it is effectively immutable
                                  _fromIndex: Int = 0)
  extends (ScType => ScType) {

  import ScSubstitutor._

  private[recursiveUpdate] val substitutions = _substitutions
  private[recursiveUpdate] val fromIndex = _fromIndex
  private[recursiveUpdate] def next: ScSubstitutor = new ScSubstitutor(substitutions, fromIndex + 1)

  private[recursiveUpdate] lazy val hasNonLeafSubstitutions: Boolean = hasNonLeafSubstitutionsImpl

  private[this] def hasNonLeafSubstitutionsImpl: Boolean = {
    var idx = fromIndex
    while (idx < substitutions.length) {
      if (!substitutions(idx).isInstanceOf[LeafSubstitution])
        return true

      idx += 1
    }
    false
  }

  //positive fromIndex is possible only for temporary substitutors during recursive update
  private def assertFullSubstitutor(): Unit = LOG.assertTrue(fromIndex == 0)

  override def apply(`type`: ScType): ScType = {
    if (cacheSubstitutions)
      cache ++= this.allTypeParamsMap

    recursiveUpdateImpl(`type`)(SubtypeUpdaterNoVariance, Set.empty)
  }

  //This method allows application of different `Update` functions in a single pass (see ScSubstitutor).
  //WARNING: If several updates are used, they should be applicable only for leaf types, e.g. which return themselves
  //from `updateSubtypes` method
  @tailrec
  private[recursiveUpdate] def recursiveUpdateImpl(scType: ScType,
                                                   variance: Variance = Covariant,
                                                   isLazySubtype: Boolean = false)
                                                  (implicit subtypeUpdater: SubtypeUpdater,
                                                   visited: Set[ScType] = Set.empty): ScType = {
    if (fromIndex >= substitutions.length || visited(scType)) scType
    else {
      val currentUpdate = substitutions(fromIndex)

      currentUpdate(scType, variance) match {
        case ReplaceWith(res) =>
          next.recursiveUpdateImpl(res, variance, isLazySubtype)(subtypeUpdater, visited)
        case Stop => scType
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + scType else visited

          if (hasNonLeafSubstitutions) {
            val withCurrentUpdate = subtypeUpdater.updateSubtypes(scType, variance, ScSubstitutor(currentUpdate))(newVisited)
            next.recursiveUpdateImpl(withCurrentUpdate, variance)(subtypeUpdater, Set.empty)
          }
          else {
            subtypeUpdater.updateSubtypes(scType, variance, this)(newVisited)
          }
      }
    }
  }


  def followed(other: ScSubstitutor): ScSubstitutor = {
    assertFullSubstitutor()

    if (this.isEmpty)
      if (other.fromIndex > 0) new ScSubstitutor(other.substitutions)
      else other
    else if (other.isEmpty) this
    else {
      val thisLength = substitutions.length
      val newLength = thisLength + other.substitutions.length

      if (newLength > followLimit)
        LOG.error("Too much followers for substitutor: " + this.toString)

      val newArray = new Array[Update](newLength)
      substitutions.copyToArray(newArray, 0)
      other.substitutions.copyToArray(newArray, thisLength)

      new ScSubstitutor(newArray)
    }
  }

  def followUpdateThisType(tp: ScType): ScSubstitutor = {
    assertFullSubstitutor()

    ScSubstitutor(tp).followed(this)
  }

  def withBindings(from: Seq[TypeParameter], target: Seq[TypeParameter]): ScSubstitutor = {
    assertFullSubstitutor()

    def simple: ScSubstitutor = bind(from, target)(TypeParameterType(_))

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

  override def hashCode(): Int = MurmurHash3.arrayHash(substitutions)

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
  val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor")

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  val empty: ScSubstitutor = new ScSubstitutor(Array.empty)

  private[recursiveUpdate] def apply(s: Update) = new ScSubstitutor(Array(s))

  private val followLimit = 800

  var cacheSubstitutions = false

  var cache: LongMap[ScType] = LongMap.empty

  def apply(tvMap: LongMap[ScType]): ScSubstitutor = {
    if (tvMap.isEmpty) ScSubstitutor.empty
    else ScSubstitutor(TypeParamSubstitution(tvMap))
  }

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
    subst.substitutions.collectFirstByType[ThisTypeSubstitution, ScType](_.target)
  }
}