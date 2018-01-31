package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, params}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, TypeParamId, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor.LazyDepMethodTypes
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.result._

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

  private def extractTpt(tpt: TypeParameterType, t: ScType): ScType = {
    if (tpt.arguments.isEmpty) t
    else t match {
      case ParameterizedType(designator, _) => designator
      case _ => t
    }
  }

  protected def substInternal(t: ScType): ScType = {
    import t.projectContext

    var result: ScType = t
    val visitor = new ScalaTypeVisitor {
      override def visitTypePolymorphicType(t: ScTypePolymorphicType): Unit = {
        val ScTypePolymorphicType(internalType, typeParameters) = t
        result = ScTypePolymorphicType(substInternal(internalType),
          typeParameters.map {
            case TypeParameter(parameters, lowerType, upperType, psiTypeParameter) =>
              TypeParameter(
                parameters, // todo: is it important here to update?
                substInternal(lowerType),
                substInternal(upperType),
                psiTypeParameter)
          })
      }

      override def visitAbstractType(a: ScAbstractType): Unit = {
        val parameterType = a.parameterType
        result = tvMap.get(parameterType.typeParamId) match {
          case None => a
          case Some(v) => v match {
            case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => a
            case _ => extractTpt(parameterType, v)
          }
        }
      }

      override def visitMethodType(m: ScMethodType): Unit = {
        val ScMethodType(retType, params, isImplicit) = m
        implicit val elementScope = m.elementScope
        result = ScMethodType(substInternal(retType),
          params.map(p => p.copy(paramType = substInternal(p.paramType),
            expectedType = substInternal(p.expectedType), defaultType = p.defaultType.map(substInternal))), isImplicit)
      }

      override def visitUndefinedType(u: UndefinedType): Unit = {
        val parameterType = u.parameterType
        result = tvMap.get(parameterType.typeParamId) match {
          case None => u
          case Some(v) => v match {
            case tpt: TypeParameterType if tpt.psiTypeParameter == parameterType.psiTypeParameter => u
            case _ => extractTpt(parameterType, v)
          }
        }
      }

      override def visitTypeParameterType(tpt: TypeParameterType): Unit = {
        result = tvMap.get(tpt.typeParamId) match {
          case None => tpt
          case Some(v) => extractTpt(tpt, v)
        }
      }

      override def visitDesignatorType(d: ScDesignatorType): Unit = {
        val depMethodType = depMethodTypes.flatMap(_.value.collectFirst {
          case (parameter: Parameter, tp: ScType) if parameter.paramInCode.contains(d.element) => tp
        })
        result = depMethodType.getOrElse(d)
      }

      override def visitThisType(th: ScThisType): Unit = {
        def hasRecursiveThisType(tp: ScType, clazz: ScTemplateDefinition): Boolean = {
          var found = false
          tp.recursiveUpdate {
            case ScThisType(`clazz`) if !found =>
              found = true
              Stop
            case _ => if (found) Stop else ProcessSubtypes
          }
          found
        }

        def containingClassType(tp: ScType): ScType = tp match {
          case ScThisType(template) =>
            template.containingClass match {
              case td: ScTemplateDefinition => ScThisType(td)
              case _ => null
            }
          case ScProjectionType(newType, _) => newType
          case ParameterizedType(ScProjectionType(newType, _), _) => newType
          case _ => null
        }

        def isSameOrInheritor(clazz: PsiClass, thisTp: ScThisType): Boolean =
          clazz == thisTp.element || isInheritorDeep(clazz, thisTp.element)

        def hasSameOrInheritor(compound: ScCompoundType, thisTp: ScThisType) = {
          compound.components
            .exists {
              _.extractClass
                .exists(isSameOrInheritor(_, thisTp))
            }
        }

        @tailrec
        def isSubtype(target: ScType, thisTp: ScThisType): Boolean =
          target.extractDesignated(expandAliases = true) match {
            case Some(typeParam: PsiTypeParameter) =>
              target match {
                case t: TypeParameterType =>
                  isSubtype(t.upperType, thisTp)
                case p: ParameterizedType =>
                  p.designator match {
                    case TypeParameterType(_, _, upperType, _) =>
                      isSubtype(p.substitutor.subst(upperType), thisTp)
                    case _ =>
                      isSameOrInheritor(typeParam, thisTp)
                  }
                case _ => isSameOrInheritor(typeParam, thisTp)
              }
            case Some(t: ScTypeDefinition) =>
              if (isSameOrInheritor(t, thisTp)) true
              else t.selfType match {
                case Some(selfTp) => isSubtype(selfTp, thisTp)
                case _ => false
              }
            case Some(cl: PsiClass) =>
              isSameOrInheritor(cl, thisTp)
            case Some(named: ScTypedDefinition) =>
              isSubtype(named.`type`().getOrAny, thisTp)
            case _ =>
              target match {
                case compound: ScCompoundType =>
                  hasSameOrInheritor(compound, thisTp)
                case _ =>
                  false
              }
          }

        @tailrec
        def doUpdateThisType(thisTp: ScThisType, target: ScType): ScType = {
          if (isSubtype(target, thisTp)) target
          else {
            val targetContext = containingClassType(target)
            if (targetContext != null) doUpdateThisType(thisTp, targetContext)
            else thisTp
          }
        }

        result = updateThisType match {
          case Some(target) if !hasRecursiveThisType(target, th.element) => //todo: hack to avoid infinite recursion during type substitution
            doUpdateThisType(th, target)
          case _ => th
        }
      }

      override def visitExistentialArgument(s: ScExistentialArgument): Unit = {
        val ScExistentialArgument(name, args, lower, upper) = s
        result = ScExistentialArgument(name, args.map(t => substInternal(t).asInstanceOf[TypeParameterType]),
          substInternal(lower), substInternal(upper))
      }

      override def visitExistentialType(ex: ScExistentialType): Unit = {
        val ScExistentialType(q, wildcards) = ex
        result = new ScExistentialType(substInternal(q),
          wildcards.map(ex => substInternal(ex).asInstanceOf[ScExistentialArgument]))
      }

      override def visitParameterizedType(pt: ParameterizedType): Unit = {
        val typeArgs = pt.typeArguments
        result = pt.designator match {
          case tpt: TypeParameterType =>
            tvMap.get(tpt.typeParamId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (tpt.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs.map(substInternal))
                }
              case _ =>
                substInternal(tpt) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case u@UndefinedType(parameterType, _) =>
            tvMap.get(parameterType.typeParamId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (parameterType.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(u) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case a@ScAbstractType(parameterType, _, _) =>
            tvMap.get(parameterType.typeParamId) match {
              case Some(param: ScParameterizedType) if pt != param =>
                if (parameterType.arguments.isEmpty) {
                  substInternal(param) //to prevent types like T[A][A]
                } else {
                  ScParameterizedType(param.designator, typeArgs map substInternal)
                }
              case _ =>
                substInternal(a) match {
                  case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
                  case des => ScParameterizedType(des, typeArgs map substInternal)
                }
            }
          case designator =>
            substInternal(designator) match {
              case ParameterizedType(des, _) => ScParameterizedType(des, typeArgs map substInternal)
              case des => ScParameterizedType(des, typeArgs map substInternal)
            }
        }
      }

      override def visitJavaArrayType(j: JavaArrayType): Unit = {
        result = JavaArrayType(substInternal(j.argument))
      }

      override def visitProjectionType(p: ScProjectionType): Unit = {
        result = ScProjectionType(substInternal(p.projected), p.element)
      }

      override def visitCompoundType(comp: ScCompoundType): Unit = {
        val ScCompoundType(comps, signatureMap, typeMap) = comp

        def substTypeParam: TypeParameter => TypeParameter = {
          case TypeParameter(typeParameters, lowerType, upperType, psiTypeParameter) =>
            TypeParameter(
              typeParameters.map(substTypeParam),
              substInternal(lowerType),
              substInternal(upperType),
              psiTypeParameter)
        }

        val middleRes = ScCompoundType(comps.map(substInternal), signatureMap.map {
          case (s: Signature, tp: ScType) =>
            val pTypes: Seq[Seq[() => ScType]] = s.substitutedTypes.map(_.map(f => () => substInternal(f())))
            val tParams = s.typeParams.subst(substTypeParam)
            val rt: ScType = substInternal(tp)
            (new Signature(s.name, pTypes, tParams,
              ScSubstitutor.empty, s.namedElement match {
                case fun: ScFunction =>
                  ScFunction.getCompoundCopy(pTypes.map(_.map(_ ()).toList), tParams.toList, rt, fun)
                case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                case named => named
              }, s.hasRepeatedParam), rt)
        }, typeMap.map {
          case (s, sign) => (s, sign.updateTypes(substInternal))
        })
        //todo: this is ugly workaround for
        result = updateThisType match {
          case Some(thisType@ScDesignatorType(param: ScParameter)) =>
            val paramType = param.getRealParameterType.getOrAny
            if (paramType.conforms(middleRes)) thisType
            else middleRes
          case _ => middleRes
        }
      }
    }
    t.visitType(visitor)
    result
  }
}