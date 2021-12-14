package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContextOwner
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.smartEquivalence

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait BoundsUtil {
  self: BoundsBase with ProjectContextOwner =>

  def typeParametersBound(
    lhsParams: Seq[TypeParameter],
    rhsParams: Seq[TypeParameter],
    boundKind: Bound,
    checkWeak: Boolean = false
  ): Seq[TypeParameter] =
    lhsParams.zip(rhsParams).map {
      case (p1, p2) =>
        val (lower, upper) = boundKind match {
          case Bound.Glb => (lub(p1.lowerType, p2.lowerType, checkWeak), glb(p1.upperType, p2.upperType, checkWeak))
          case Bound.Lub => (glb(p1.lowerType, p2.lowerType, checkWeak), lub(p1.upperType, p2.upperType, checkWeak))
        }
        TypeParameter.light(p1.name, Seq.empty, lower, upper)
    }

  protected class BaseClassInfo(_tp: ScType) {
    val tp: ScType = _tp match {
      case ex: ScExistentialType => ex.quantified
      case lit: ScLiteralType    => lit.wideType
      case other                 => other
    }

    private val typeNamedElement: Option[(PsiNamedElement, ScSubstitutor)] =
      tp.extractDesignatedType(false).collect {
        case tpl @ (_: PsiClass | _: ScTypeAlias | _: ScTypeParam, _) => tpl
      }

    def isEmpty: Boolean = typeNamedElement.isEmpty

    val projectionOption: Option[ScType] = projectionOptionImpl(tp, Set.empty)

    @tailrec
    private def projectionOptionImpl(tp: ScType, visited: Set[ScType]): Option[ScType] = {
      if (visited(tp))
        return None

      tp match {
        case ParameterizedType(des, _) => projectionOptionImpl(des, visited + tp)
        case proj @ ScProjectionType(p, _) =>
          proj.actualElement match {
            case _: PsiClass => Some(p)
            case t: ScTypeAliasDefinition =>
              t.aliasedType.toOption match {
                case None          => None
                case Some(aliased) => projectionOptionImpl(proj.actualSubst(aliased), visited + tp)
              }
            case _: ScTypeAliasDeclaration => Some(p)
            case _                         => None
          }
        case ScDesignatorType(t: ScTypeAliasDefinition) =>
          t.aliasedType.toOption match {
            case None          => None
            case Some(aliased) => projectionOptionImpl(aliased, visited + tp)
          }
        case _ => None
      }
    }

    def getSuperClasses: Seq[BaseClassInfo] = {
      val subst = this.projectionOption match {
        case Some(proj) => ScSubstitutor(proj)
        case None       => ScSubstitutor.empty
      }

      (getNamedElement match {
        case t: ScTemplateDefinition =>
          t.superTypes.map(tp => new BaseClassInfo(subst(tp))).filter(!_.isEmpty)
        case p: PsiClass =>
          p.getSupers.toSeq.map(cl => new BaseClassInfo(ScalaType.designator(cl))).filter(!_.isEmpty)
        case _: ScTypeAlias =>
          val upper = tp.aliasType.map(_.upper.getOrAny).getOrElse(Any)
          extractBaseClassInfo(upper).filter(!_.isEmpty)
        case param: ScTypeParam => extractBaseClassInfo(param.upperBound.getOrAny)
      }): @nowarn("msg=unreachable code")
    }

    def isSameOrBaseClass(other: BaseClassInfo): Boolean =
      (getNamedElement, other.getNamedElement) match {
        case (lp: ScTypeParam, rp: ScTypeParam) => smartEquivalence(lp, rp)
        case (base: PsiClass, inheritor: PsiClass) =>
          inheritor.sameOrInheritor(base)
        case (base, inheritor: ScTypeAlias) =>
          if (smartEquivalence(base, inheritor)) return true
          for (opt <- other.getSuperClasses) {
            if (isSameOrBaseClass(opt)) return true
          }
          false
        case _ => false //class can't be inheritor of type alias
      }

    def getNamedElement: PsiNamedElement = typeNamedElement.get._1

    def getTypeParameters: Array[PsiTypeParameter] = getNamedElement match {
      case tp: ScTypeParam => tp.typeParameters.toArray
      case a: ScTypeAlias  => a.typeParameters.toArray
      case p: PsiClass     => p.getTypeParameters
    }

    def baseDesignator: ScType =
      projectionOption match {
        case Some(proj) => ScProjectionType(proj, getNamedElement)
        case None =>
          getNamedElement match {
            case tp: ScTypeParam => TypeParameterType(tp)
            case other           => ScalaType.designator(other)
          }
      }

    def superSubstitutor(other: BaseClassInfo): Option[ScSubstitutor] = {
      def superSubstitutor(
        base:     PsiClass,
        drv:      PsiClass,
        drvSubst: ScSubstitutor,
        visited:  mutable.Set[PsiClass]
      ): Option[ScSubstitutor] =
        if (base.getManager.areElementsEquivalent(base, drv)) Some(drvSubst)
        else {
          if (visited.contains(drv)) None
          else {
            visited += drv
            val superTypes: Seq[ScType] = drv match {
              case td: ScTemplateDefinition => td.superTypes
              case _                        => drv.getSuperTypes.map(_.toScType()).toSeq
            }

            val iterator = superTypes.iterator
            while (iterator.hasNext) {
              val st = iterator.next()
              st.extractClassType match {
                case None =>
                case Some((c, s)) =>
                  superSubstitutor(base, c, s, visited) match {
                    case None        =>
                    case Some(subst) => return Some(subst.followed(drvSubst))
                  }
              }
            }
            None
          }
        }
      (getNamedElement, other.getNamedElement) match {
        case (base: PsiClass, drv: PsiClass) =>
          superSubstitutor(base, drv, other.typeNamedElement.get._2, mutable.Set.empty)
        case (base, inheritor: ScTypeAlias) =>
          if (smartEquivalence(base, inheritor)) {
            other.tp match {
              case ParameterizedType(_, typeArgs) =>
                val substitutor = ScSubstitutor.bind(other.getTypeParameters, typeArgs)
                return Some(substitutor)
              case _ => return None
            }
          }
          for (opt <- other.getSuperClasses) {
            this.superSubstitutor(opt) match {
              case Some(res) => return Some(res)
              case _         =>
            }
          }
          None
        case _ => None //class can't be inheritor of type alias
      }
    }
  }

  protected def calcForTypeParamWithoutVariance(
    substed1:             ScType,
    substed2:             ScType,
    checkWeak:            Boolean,
    stopAddingUpperBound: Boolean,
    count:                Int = 1
  ): (ScType, Option[ScExistentialArgument]) =
    if (substed1.equiv(substed2)) (substed1, None)
    else {
      if (substed1.conforms(substed2)) {
        val ex = ScExistentialArgument("_$" + count, List.empty, substed1, substed2)
        (ex, Some(ex))
      } else if (substed2.conforms(substed1)) {
        val ex = ScExistentialArgument("_$" + count, List.empty, substed2, substed1)
        (ex, Some(ex))
      } else {
        (substed1, substed2) match {
          case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2)) =>
            val newLub =
              if (stopAddingUpperBound) Any
              else lubInner(upper, upper2, checkWeak, stopAddingUpperBound = true)

            (arg.copyWithBounds(glb(lower, lower2, checkWeak), newLub), None)
          case (arg @ ScExistentialArgument(_, _, lower, upper), _) =>
            val newLub =
              if (stopAddingUpperBound) Any
              else lubInner(upper, substed2, checkWeak, stopAddingUpperBound = true)

            (arg.copyWithBounds(glb(lower, substed2), newLub), None)
          case (_, arg @ ScExistentialArgument(_, _, lower, upper)) =>
            val newLub =
              if (stopAddingUpperBound) Any
              else lubInner(upper, substed1, checkWeak, stopAddingUpperBound = true)
            (arg.copyWithBounds(glb(lower, substed1), newLub), None)
          case _ =>
            val newGlb = glb(substed1, substed2)
            if (!stopAddingUpperBound) {
              val newLub = lubInner(substed1, substed2, checkWeak = false, stopAddingUpperBound = true)
              val ex     = ScExistentialArgument("_$" + count, List.empty, newGlb, newLub)
              (ex, Some(ex))
            } else {
              //todo: this is wrong, actually we should pick lub, just without merging parameters in this method
              val ex = ScExistentialArgument("_$" + count, List.empty, newGlb, Any)
              (ex, Some(ex))
            }
        }
      }
    }

  protected def getTypeForAppending(
    clazz1:               BaseClassInfo,
    clazz2:               BaseClassInfo,
    baseClass:            BaseClassInfo,
    depth:                Int,
    checkWeak:            Boolean,
    stopAddingUpperBound: Boolean
  ): ScType = {
    val baseClassDesignator = baseClass.baseDesignator
    val baseClassTps        = baseClass.getTypeParameters

    if (baseClassTps.isEmpty) return baseClassDesignator
    (baseClass.superSubstitutor(clazz1), baseClass.superSubstitutor(clazz2)) match {
      case (Some(superSubst1), Some(superSubst2)) =>
        val tp          = ScParameterizedType(baseClassDesignator, baseClassTps.map(TypeParameterType(_)).toSeq)
        val tp1         = superSubst1(tp).asInstanceOf[ScParameterizedType]
        val tp2         = superSubst2(tp).asInstanceOf[ScParameterizedType]
        val resTypeArgs = ArrayBuffer.empty[ScType]
        val wildcards   = ArrayBuffer.empty[ScExistentialArgument]

        baseClassTps.zipWithIndex.map {
          case (tp, idx) =>
            val substed1 = tp1.typeArguments.apply(idx)
            val substed2 = tp2.typeArguments.apply(idx)
            resTypeArgs += (tp match {
              case scp: ScTypeParam if scp.isCovariant =>
                if (depth > 0) lubInner(substed1, substed2, depth - 1, checkWeak)(stopAddingUpperBound)
                else           Any
              case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2, checkWeak)
              case _ =>
                val (v, ex) =
                  calcForTypeParamWithoutVariance(
                    substed1,
                    substed2,
                    checkWeak,
                    stopAddingUpperBound,
                    count = wildcards.length + 1
                  )

                wildcards ++= ex
                v
            })
        }

        if (wildcards.isEmpty)
          ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq)
        else
          ScExistentialType(ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq))
      case _ => Any
    }
  }

  protected def getLeastSuperClasses(
    leftClasses:  Seq[BaseClassInfo],
    rightClasses: Seq[BaseClassInfo]
  ): Array[(BaseClassInfo, Int, Int)] = {
    val res = new ArrayBuffer[(BaseClassInfo, Int, Int)]

    def addClass(baseClassToAdd: BaseClassInfo, x: Int, y: Int): Unit = {
      var i     = 0
      var break = false

      while (!break && i < res.length) {
        val alreadyFound = res(i)._1
        if (baseClassToAdd.isSameOrBaseClass(alreadyFound)) {
          break = true //todo: join them somehow?
        } else if (alreadyFound.isSameOrBaseClass(baseClassToAdd)) {
          res(i) = (baseClassToAdd, x, y)
          break  = true
        }
        i = i + 1
      }

      if (!break) {
        res += ((baseClassToAdd, x, y))
      }
    }

    def checkClasses(
      leftClasses: Seq[BaseClassInfo],
      baseIndex:   Int = -1,
      visited:     mutable.HashSet[PsiElement] = mutable.HashSet.empty
    ): Unit = {
      ProgressManager.checkCanceled()

      if (leftClasses.isEmpty) return

      val leftIterator = leftClasses.iterator
      var i            = 0

      while (leftIterator.hasNext) {
        val leftClass     = leftIterator.next()
        val rightIterator = rightClasses.iterator
        var break         = false
        var j             = 0

        while (!break && rightIterator.hasNext) {
          val rightClass = rightIterator.next()
          if (leftClass.isSameOrBaseClass(rightClass)) {
            addClass(leftClass, if (baseIndex == -1) i else baseIndex, j)
            break = true
          } else {
            val element = leftClass.getNamedElement
            if (!visited.contains(element)) {
              visited.add(element)
              checkClasses(leftClass.getSuperClasses, if (baseIndex == -1) i else baseIndex, visited)
            }
          }
          j += 1
        }
        i += 1
      }
    }

    checkClasses(leftClasses)
    res.toArray
  }

  def extractBaseClassInfo(tp: ScType): Seq[BaseClassInfo] = tp match {
    case ScCompoundType(comps, _, _) => comps.map(new BaseClassInfo(_))
    case orTp: ScOrType              => extractBaseClassInfo(orTp.join)
    case ScAndType(lhs, rhs)         => Seq(new BaseClassInfo(lhs), new BaseClassInfo(rhs))
    case t                           => Seq(new BaseClassInfo(t))
  }

  sealed trait Bound {
    def inverse: Bound
  }

  object Bound {
    final case object Lub extends Bound {
      override def inverse: Bound = Glb
    }

    final case object Glb extends Bound {
      override def inverse: Bound = Lub
    }
  }
}
