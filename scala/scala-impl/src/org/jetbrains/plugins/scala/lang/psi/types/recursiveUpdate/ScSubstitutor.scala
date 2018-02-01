package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import java.util.Objects

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{TypeParamId, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor.LazyDepMethodTypes
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScCompoundType, ScType}

import scala.annotation.tailrec
import scala.collection.Seq
import scala.collection.immutable.LongMap
import scala.language.implicitConversions

/**
  * @author ven
  */
object ScSubstitutor {
  val empty: ScSubstitutor = new ScSubstitutor(LongMap.empty, None) {
    override def toString: String = "Empty substitutor"

    override def subst(t: ScType): ScType = t

    override def substInternal(t: ScType): ScType = t
  }

  val key: Key[ScSubstitutor] = Key.create("scala substitutor key")

  private val followLimit = 800

  var cacheSubstitutions = false

  var cache: LongMap[ScType] = LongMap.empty

  def apply(tvMap: LongMap[ScType]) =
    new ScSubstitutor(tvMap)

  def apply(updateThisType: ScType): ScSubstitutor =
    new ScSubstitutor(updateThisType = Some(updateThisType))

  def apply(dependentMethodTypes: () => Map[Parameter, ScType]): ScSubstitutor =
    new ScSubstitutor(depMethodTypes = Some(dependentMethodTypes))

  def bind[T: TypeParamId](typeParamsLike: Seq[T])(toScType: T => ScType): ScSubstitutor = {
    val tvMap = buildMap(typeParamsLike, typeParamsLike)(toScType)
    new ScSubstitutor(tvMap)
  }

  def bind[T: TypeParamId, S](typeParamsLike: Seq[T], targets: Seq[S])(toScType: S => ScType): ScSubstitutor = {
    val tvMap = buildMap(typeParamsLike, targets)(toScType)
    new ScSubstitutor(tvMap)
  }

  def bind[T: TypeParamId](typeParamsLike: Seq[T], types: Seq[ScType]): ScSubstitutor = {
    val tvMap = buildMap(typeParamsLike, types)(identity)
    new ScSubstitutor(tvMap)
  }

  class LazyDepMethodTypes(private var fun: () => Map[Parameter, ScType]) {
    lazy val value: Map[Parameter, ScType] = {
      val res = fun()
      fun = null
      res
    }
  }

  object LazyDepMethodTypes {
    implicit def toValue(lz: LazyDepMethodTypes): Map[Parameter, ScType] = lz.value

    implicit def toLazy(fun: () => Map[Parameter, ScType]): LazyDepMethodTypes = new LazyDepMethodTypes(fun)
  }

  @tailrec
  def updateThisTypeDeep(subst: ScSubstitutor): Option[ScType] = {
    subst.updateThisType match {
      case s if s.isDefined => s
      case _ =>
        val follower = subst.follower

        if (follower.nonEmpty) updateThisTypeDeep(follower.get)
        else None
    }
  }

  private def buildMap[T, S](typeParamsLike: Seq[T],
                             types: Seq[S],
                             initial: LongMap[ScType] = LongMap.empty)
                            (toScType: S => ScType)
                            (implicit ev: TypeParamId[T]): LongMap[ScType] = {
    val iterator1 = typeParamsLike.iterator
    val iterator2 = types.iterator
    var result = initial
    while (iterator1.hasNext && iterator2.hasNext) {
      result = result.updated(ev.typeParamId(iterator1.next()), toScType(iterator2.next()))
    }
    result
  }
}

class ScSubstitutor private(private val tvMap: LongMap[ScType] = LongMap.empty,
                            private val updateThisType: Option[ScType] = None,
                            private val follower: Option[ScSubstitutor] = None,
                            private val depMethodTypes: Option[LazyDepMethodTypes] = None) {

  override def toString: String = {
    val mapText = tvMap.map {
      case (id, tp) => params.typeParamName(id) + " -> " + tp.toString
    }.mkString("Map(", ", ", ")")

    val followerText = if (follower.nonEmpty) " >> " + follower.get.toString else ""
    s"ScSubstitutor($mapText, $updateThisType)$followerText"
  }

  override def hashCode(): Int = Objects.hash(tvMap, updateThisType, follower, depMethodTypes)

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: ScSubstitutor => (other eq this) ||
      other.tvMap == tvMap &&
        other.updateThisType == updateThisType &&
        other.follower == follower &&
        other.depMethodTypes == depMethodTypes
    case _ => false
  }

  def withBindings(from: Seq[TypeParameter], target: Seq[TypeParameter]): ScSubstitutor = {
    val newMap = ScSubstitutor.buildMap(from, target, tvMap)(TypeParameterType(_))
    new ScSubstitutor(newMap, updateThisType, follower, depMethodTypes)
  }

  def followUpdateThisType(tp: ScType): ScSubstitutor = {
    tp match {
      case ScThisType(template) =>
        var zSubst = ScSubstitutor(ScThisType(template))
        var placer = template.getContext
        while (placer != null) {
          placer match {
            case t: ScTemplateDefinition => zSubst = zSubst.followed(
              ScSubstitutor(ScThisType(t))
            )
            case _ =>
          }
          placer = placer.getContext
        }
        zSubst.followed(this)
      case _ => ScSubstitutor(tp).followed(this)
    }
  }

  def followed(s: ScSubstitutor): ScSubstitutor = followed(s, 0)

  private def followed(s: ScSubstitutor, level: Int): ScSubstitutor = {
    @tailrec
    def hasFollower(th: ScSubstitutor, s: ScSubstitutor): Boolean = th.follower match {
      case None => false
      case Some(f) => (f eq s) || hasFollower(f, s)
    }

    if (level > ScSubstitutor.followLimit)
      throw new RuntimeException("Too much followers for substitutor: " + this.toString)

    val trivialOrRecursive = s == null || s.isEmpty || (s eq this) || (level == 0 && hasFollower(this, s))

    if (trivialOrRecursive) this
    else if (this.isEmpty) s
    else {
      val newFollower = Option(if (follower.nonEmpty) follower.get.followed(s, level + 1) else s)
      new ScSubstitutor(tvMap, updateThisType, newFollower, depMethodTypes)
    }
  }

  def isEmpty: Boolean = (this eq ScSubstitutor.empty) || this == ScSubstitutor.empty

  def subst(t: ScType): ScType = try {
    if (ScSubstitutor.cacheSubstitutions) ScSubstitutor.cache ++= this.tvMap
    if (follower.nonEmpty) follower.get.subst(substInternal(t)) else substInternal(t)
  } catch {
    case s: StackOverflowError =>
      throw new RuntimeException("StackOverFlow during ScSubstitutor.subst(" + t + ") this = " + this, s)
  }

  private val substitution: PartialFunction[ScType, ScType] = {
    case a: ScAbstractType     => updatedAbstract(a)
    case u: UndefinedType      => updatedUndefined(u)
    case t: TypeParameterType  => updatedTypeParameter(t)
    case d: ScDesignatorType   => updatedDesignator(d)
    case t: ScThisType         => updatedThisType(t)
  }

  protected def substInternal(t: ScType): ScType = t.updateRecursively(substitution)

  private def hasRecursiveThisType(tp: ScType, clazz: ScTemplateDefinition): Boolean =
    tp.subtypeExists {
      case ScThisType(`clazz`) => true
      case _ => false
    }

  private def containingClassType(tp: ScType): ScType = tp match {
    case ScThisType(template) =>
      template.containingClass match {
        case td: ScTemplateDefinition => ScThisType(td)
        case _ => null
      }
    case ScProjectionType(newType, _) => newType
    case ParameterizedType(ScProjectionType(newType, _), _) => newType
    case _ => null
  }

  private def isSameOrInheritor(clazz: PsiClass, thisTp: ScThisType): Boolean =
    clazz == thisTp.element || isInheritorDeep(clazz, thisTp.element)

  private def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType) = {
    compound.components
      .exists {
        _.extractClass
          .exists(isSameOrInheritor(_, thisTp))
      }
  }

  @tailrec
  private def isMoreNarrow(target: ScType, thisTp: ScThisType): Boolean =
    target.extractDesignated(expandAliases = true) match {
      case Some(typeParam: PsiTypeParameter) =>
        target match {
          case t: TypeParameterType =>
            isMoreNarrow(t.upperType, thisTp)
          case p: ParameterizedType =>
            p.designator match {
              case TypeParameterType(_, _, upperType, _) =>
                isMoreNarrow(p.substitutor.subst(upperType), thisTp)
              case _ =>
                isSameOrInheritor(typeParam, thisTp)
            }
          case _ => isSameOrInheritor(typeParam, thisTp)
        }
      case Some(t: ScTypeDefinition) =>
        if (isSameOrInheritor(t, thisTp)) true
        else t.selfType match {
          case Some(selfTp) => isMoreNarrow(selfTp, thisTp)
          case _ => false
        }
      case Some(cl: PsiClass) =>
        isSameOrInheritor(cl, thisTp)
      case Some(named: ScTypedDefinition) =>
        isMoreNarrow(named.`type`().getOrAny, thisTp)
      case _ =>
        target match {
          case compound: ScCompoundType =>
            hasSameOrInheritor(compound, thisTp)
          case _ =>
            false
        }
    }

  @tailrec
  private def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType = {
    if (isMoreNarrow(target, thisTp)) target
    else {
      val targetContext = containingClassType(target)
      if (targetContext != null) doUpdateThisType(thisTp, targetContext)
      else thisTp
    }
  }

  private def updatedThisType(thisTp: ScThisType): ScType = {
    updateThisType match {
      case Some(target) if !hasRecursiveThisType(target, thisTp.element) => //todo: hack to avoid infinite recursion during type substitution
        doUpdateThisType(thisTp, target)
      case _ => thisTp
    }
  }

  private def updatedDesignator(d: ScDesignatorType): ScType = {
    val depMethodType = depMethodTypes.flatMap(_.value.collectFirst {
      case (parameter: Parameter, tp: ScType) if parameter.paramInCode.contains(d.element) => tp
    })
    depMethodType.getOrElse(d)
  }


  private def updatedAbstract(a: ScAbstractType): ScType = {
    val parameterType = a.parameterType
    tvMap.get(parameterType.typeParamId) match {
      case None => a
      case Some(v) => v match {
        case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => a
        case _ => extractDesignator(parameterType, v)
      }
    }
  }

  private def updatedUndefined(u: UndefinedType): ScType = {
    val parameterType = u.parameterType
    tvMap.get(parameterType.typeParamId) match {
      case None => u
      case Some(v) => v match {
        case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => u
        case _ => extractDesignator(parameterType, v)
      }
    }
  }

  private def updatedTypeParameter(tpt: TypeParameterType): ScType = {
    tvMap.get(tpt.typeParamId) match {
      case None => tpt
      case Some(v) => extractDesignator(tpt, v)
    }
  }

  private def extractDesignator(tpt: TypeParameterType, t: ScType): ScType = {
    if (tpt.arguments.isEmpty) t
    else t match {
      case ParameterizedType(designator, _) => designator
      case _ => t
    }
  }
}