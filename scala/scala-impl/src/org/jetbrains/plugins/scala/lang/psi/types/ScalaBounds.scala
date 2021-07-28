package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.smartEquivalence

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait ScalaBounds extends api.Bounds {
  typeSystem: api.TypeSystem =>

  import ScalaBounds._

  override def glb(t1: ScType, t2: ScType, checkWeak: Boolean = false): ScType = {
    if (conforms(t1, t2, checkWeak)) t1
    else if (conforms(t2, t1, checkWeak)) t2
    else {
      (t1, t2) match {
        case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2)) =>
          arg.copyWithBounds(lub(lower, lower2, checkWeak), glb(upper, upper2, checkWeak))
        case (arg @ ScExistentialArgument(_, _, lower, upper), _) =>
          arg.copyWithBounds(lub(lower, t2, checkWeak), glb(upper, t2))
        case (_, arg @ ScExistentialArgument(_, _, lower, upper)) =>
          arg.copyWithBounds(lub(lower, t1, checkWeak), glb(upper, t1))
        case (ex: ScExistentialType, _) => glb(ex.quantified, t2, checkWeak).unpackedType
        case (_, ex: ScExistentialType) => glb(t1, ex.quantified, checkWeak).unpackedType
        case (lhs: ScTypePolymorphicType, rhs: ScTypePolymorphicType) =>
          polymorphicTypesBound(lhs, rhs, BoundKind.Glb, checkWeak)
        case _ => ScCompoundType(Seq(t1, t2), Map.empty, Map.empty)
      }
    }
  }

  override def glb(typez: IterableOnce[ScType], checkWeak: Boolean): ScType =
    typez.iterator.reduce(glb(_, _, checkWeak))

  private def typeParametersBound(
    lhsParams: Seq[TypeParameter],
    rhsParams: Seq[TypeParameter],
    boundKind: BoundKind,
    checkWeak: Boolean
  ): Seq[TypeParameter] =
    lhsParams.zip(rhsParams).map {
      case (p1, p2) =>
        val (lower, upper) = boundKind match {
          case BoundKind.Glb =>
            (lub(p1.lowerType, p2.lowerType, checkWeak), glb(p1.upperType, p2.upperType, checkWeak))
          case BoundKind.Lub =>
            (glb(p1.lowerType, p2.lowerType, checkWeak), lub(p1.upperType, p2.upperType, checkWeak))
        }
        TypeParameter.light(p1.name, Seq.empty, lower, upper)
    }

  def polymorphicTypesBound(
    lhs:       ScTypePolymorphicType,
    rhs:       ScTypePolymorphicType,
    boundKind: BoundKind,
    checkWeak: Boolean
  ): ScTypePolymorphicType = {
    val ScTypePolymorphicType(lhsInt, lhsParams) = lhs
    val ScTypePolymorphicType(rhsInt, rhsParams) = rhs

    val newParams = typeParametersBound(lhsParams, rhsParams, boundKind.inverse, checkWeak)
    val lhsSubst  = ScSubstitutor.bind(lhsParams, newParams)(TypeParameterType(_))
    val rhsSubst  = ScSubstitutor.bind(rhsParams, newParams)(TypeParameterType(_))

    val intTpe = boundKind match {
      case BoundKind.Glb => glb(lhsSubst(lhsInt), rhsSubst(rhsInt), checkWeak)
      case BoundKind.Lub => lub(lhsSubst(lhsInt), rhsSubst(rhsInt), checkWeak)
    }

    ScTypePolymorphicType(intTpe, newParams)
  }

  override def lub(t1: ScType, t2: ScType, checkWeak: Boolean): ScType = {
    lubInner(t1, t2, lubDepth(Seq(t1, t2)), checkWeak)(stopAddingUpperBound = false)
  }

  override def lub(seq: IterableOnce[ScType], checkWeak: Boolean): ScType = {
    seq.iterator.reduce((l: ScType, r: ScType) => lub(l, r, checkWeak))
  }

  //similar to Scala code, this code is duplicated and optimized to avoid closures.
  def typeDepth(ts: Seq[ScType]): Int = {
    @tailrec def loop(tps: Seq[ScType], acc: Int): Int = tps match {
      case tp :: rest => loop(rest, acc max tp.typeDepth)
      case _          => acc
    }
    loop(ts, 0)
  }

  def baseTypeSeqDepth(ts: Seq[ScType]): Int = {
    @tailrec def loop(tps: Seq[ScType], acc: Int): Int = tps match {
      // TODO: should be implemented according to Scala compiler sources. However concerns about performance stops me.
      case _ :: rest => loop(rest, acc max 1)
      case _          => acc
    }
    loop(ts, 0)
  }

  def lubDepth(ts: Seq[ScType]): Int = {
    val td = typeDepth(ts)
    val bd = baseTypeSeqDepth(ts)
    lubDepthAdjust(td, td max bd)
  }

  private def conforms(t1: ScType, t2: ScType, checkWeak: Boolean) = t1.conforms(t2, ConstraintSystem.empty, checkWeak).isRight

  //This weird method is copy from Scala compiler. See scala.reflect.internal.Types#lubDepthAdjust
  private def lubDepthAdjust(td: Int, bd: Int): Int = {
    if (bd <= 3) bd
    else if (bd <= 5) td max (bd - 1)
    else if (bd <= 7) td max (bd - 2)
    else (td - 1) max (bd - 3)
  }

  private class ClassLike(_tp: ScType) {
    val tp: ScType = _tp match {
      case ex: ScExistentialType => ex.quantified
      case other => other
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

    def getSuperClasses: Seq[ClassLike] = {
      val subst = this.projectionOption match {
        case Some(proj) => ScSubstitutor(proj)
        case None => ScSubstitutor.empty
      }
      (getNamedElement match {
        case t: ScTemplateDefinition => t.superTypes.map(tp => new ClassLike(subst(tp))).filter(!_.isEmpty)
        case p: PsiClass => p.getSupers.toSeq.map(cl => new ClassLike(ScalaType.designator(cl))).filter(!_.isEmpty)
        case _: ScTypeAlias =>
          val upperType: ScType = tp.aliasType.map(_.upper.getOrAny).getOrElse(Any)
          val classes: Seq[ClassLike] = {
            upperType match {
              case ScCompoundType(comps1, _, _) => comps1.map(new ClassLike(_))
              case _ => Seq(new ClassLike(upperType))
            }
          }
          classes.filter(!_.isEmpty)
        case param: ScTypeParam =>
          val upper = param.upperBound.getOrAny
          upper match {
            case ScCompoundType(comps, _, _) => comps.map(new ClassLike(_))
            case t                           => Seq(new ClassLike(t))
          }
      }): @nowarn("msg=unreachable code")
    }

    def isSameOrBaseClass(other: ClassLike): Boolean =
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

    def superSubstitutor(other: ClassLike): Option[ScSubstitutor] = {
      def superSubstitutor(base: PsiClass, drv: PsiClass, drvSubst: ScSubstitutor,
                           visited: mutable.Set[PsiClass]): Option[ScSubstitutor] = {
        if (base.getManager.areElementsEquivalent(base, drv)) Some(drvSubst) else {
          if (visited.contains(drv)) None else {
            visited += drv
            val superTypes: Seq[ScType] = drv match {
              case td: ScTemplateDefinition => td.superTypes
              case _ => drv.getSuperTypes.map {
                _.toScType()
              }.toSeq
            }
            val iterator = superTypes.iterator
            while(iterator.hasNext) {
              val st = iterator.next()
              st.extractClassType match {
                case None =>
                case Some((c, s)) => superSubstitutor(base, c, s, visited) match {
                  case None =>
                  case Some(subst) => return Some(subst.followed(drvSubst))
                }
              }
            }
            None
          }
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
              case _ =>
            }
          }
          None
        case _ => None //class can't be inheritor of type alias
      }
    }
  }

  private def lubInner(l: ScType, r: ScType, checkWeak: Boolean, stopAddingUpperBound: Boolean): ScType = {
    lubInner(l, r, lubDepth(Seq(l, r)), checkWeak)(stopAddingUpperBound)
  }

  private def lubInner(t1: ScType, t2: ScType, depth : Int, checkWeak: Boolean)(implicit stopAddingUpperBound: Boolean): ScType = {
    if (conforms(t1, t2, checkWeak)) t2
    else if (conforms(t2, t1, checkWeak)) t1
    else {
      def lubWithExpandedAliases(t1: ScType, t2: ScType): ScType = {
        (t1, t2) match {
          case (ScDesignatorType(t: ScParameter), _) =>
            lubInner(t.getRealParameterType.getOrAny, t2, depth, checkWeak)
          case (ScDesignatorType(t: ScTypedDefinition), _) if !t.isInstanceOf[ScObject] =>
            lubInner(t.`type`().getOrAny, t2, depth, checkWeak)
          case (_, ScDesignatorType(t: ScParameter)) =>
            lubInner(t1, t.getRealParameterType.getOrAny, depth, checkWeak)
          case (_, ScDesignatorType(t: ScTypedDefinition)) if !t.isInstanceOf[ScObject] =>
            lubInner(t1, t.`type`().getOrAny, depth, checkWeak)
          case (ex: ScExistentialType, _) => lubInner(ex.quantified, t2, depth, checkWeak).unpackedType
          case (_, ex: ScExistentialType) => lubInner(t1, ex.quantified, depth, checkWeak).unpackedType
          case (tpt: TypeParameterType, _) if !stopAddingUpperBound => lubInner(tpt.upperType, t2, depth - 1, checkWeak)
          case (_, tpt: TypeParameterType) if !stopAddingUpperBound => lubInner(t1, tpt.upperType, depth - 1, checkWeak)
          case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2))
            if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, lower2, checkWeak), lubInner(upper, upper2, depth - 1, checkWeak))
          case (arg @ ScExistentialArgument(_, _, lower, upper), r) if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, r, checkWeak), lubInner(upper, t2, depth - 1, checkWeak))
          case (r, arg @ ScExistentialArgument(_, _, lower, upper)) if !stopAddingUpperBound =>
            arg.copyWithBounds(glb(lower, r, checkWeak), lubInner(upper, t2, depth - 1, checkWeak))
          case (_: ValType, _: ValType) => AnyVal
          case (lit1: ScLiteralType, lit2: ScLiteralType) => lubInner(lit1.wideType, lit2.wideType, depth, checkWeak = true)
          case (JavaArrayType(arg1), JavaArrayType(arg2)) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg1, arg2, checkWeak)
            ex match {
              case Some(_) => ScExistentialType(JavaArrayType(v))
              case None    => JavaArrayType(v)
            }
          case (JavaArrayType(arg), ParameterizedType(des, args)) if args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args.head, checkWeak)
            ex match {
              case Some(_) => ScExistentialType(ScParameterizedType(des, Seq(v)))
              case None    => ScParameterizedType(des, Seq(v))
            }
          case (ParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (des.extractClass match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args.head, checkWeak)
            ex match {
              case Some(_) => ScExistentialType(ScParameterizedType(des, Seq(v)))
              case None    => ScParameterizedType(des, Seq(v))
            }
          case (JavaArrayType(_), tp) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case (tp, JavaArrayType(_)) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case (lhs: ScTypePolymorphicType, rhs: ScTypePolymorphicType) =>
            polymorphicTypesBound(lhs, rhs, BoundKind.Lub, checkWeak)
          case _ =>
            val leftClasses: Seq[ClassLike] = {
              t1 match {
                case ScCompoundType(comps1, _, _) => comps1.map(new ClassLike(_))
                case _ => Seq(new ClassLike(t1))
              }
            }
            val rightClasses: Seq[ClassLike] = {
              t2 match {
                case ScCompoundType(comps1, _, _) => comps1.map(new ClassLike(_))
                case _ => Seq(new ClassLike(t2))
              }
            }
            if (leftClasses.exists(_.isEmpty) || rightClasses.exists(_.isEmpty)) Any
            else {
              val buf = new ArrayBuffer[ScType]
              val supers: Array[(ClassLike, Int, Int)] =
                getLeastUpperClasses(leftClasses, rightClasses)
              for (sup <- supers) {
                val tp = getTypeForAppending(leftClasses(sup._2), rightClasses(sup._3), sup._1, depth, checkWeak)
                if (tp != Any) buf += tp
              }
              buf.toArray match {
                case a: Array[ScType] if a.length == 0 => Any
                case a: Array[ScType] if a.length == 1 => a(0)
                case many                              => ScCompoundType(many.toSeq, Map.empty, Map.empty)
              }
            }
          //todo: refinement for compound types
        }
      }
      lubWithExpandedAliases(t1, t2).unpackedType
    }
  }

  private def calcForTypeParamWithoutVariance(substed1: ScType, substed2: ScType, checkWeak: Boolean, count: Int = 1)
                                             (implicit stopAddingUpperBound: Boolean): (ScType, Option[ScExistentialArgument]) = {
    if (substed1 equiv substed2) (substed1, None) else {
      if (substed1 conforms substed2) {
        val ex = ScExistentialArgument("_$" + count, List.empty, substed1, substed2)
        (ex, Some(ex))
      } else if (substed2 conforms substed1) {
        val ex = ScExistentialArgument("_$" + count, List.empty, substed2, substed1)
        (ex, Some(ex))
      } else {
        (substed1, substed2) match {
          case (arg @ ScExistentialArgument(_, _, lower, upper), ScExistentialArgument(_, _, lower2, upper2)) =>
            val newLub = if (stopAddingUpperBound) Any else lubInner(upper, upper2, checkWeak, stopAddingUpperBound = true)
            (arg.copyWithBounds(glb(lower, lower2, checkWeak), newLub), None)
          case (arg @ ScExistentialArgument(_, _, lower, upper), _) =>
            val newLub = if (stopAddingUpperBound) Any else lubInner(upper, substed2, checkWeak, stopAddingUpperBound = true)
            (arg.copyWithBounds(glb(lower, substed2), newLub), None)
          case (_, arg @ ScExistentialArgument(_, _, lower, upper)) =>
            val newLub = if (stopAddingUpperBound) Any else lubInner(upper, substed1, checkWeak, stopAddingUpperBound = true)
            (arg.copyWithBounds(glb(lower, substed1), newLub), None)
          case _ =>
            val newGlb = glb(substed1, substed2)
            if (!stopAddingUpperBound) {
              val newLub = lubInner(substed1, substed2, checkWeak = false, stopAddingUpperBound = true)
              val ex = ScExistentialArgument("_$" + count, List.empty, newGlb, newLub)
              (ex, Some(ex))
            } else {
              //todo: this is wrong, actually we should pick lub, just without merging parameters in this method
              val ex = ScExistentialArgument("_$" + count, List.empty, newGlb, Any)
              (ex, Some(ex))
            }
        }
      }
    }
  }

  private def getTypeForAppending(clazz1: ClassLike, clazz2: ClassLike, baseClass: ClassLike, depth: Int, checkWeak: Boolean)
                                 (implicit stopAddingUpperBound: Boolean): ScType = {
    val baseClassDesignator = baseClass.baseDesignator
    if (baseClass.getTypeParameters.length == 0) return baseClassDesignator
    (baseClass.superSubstitutor(clazz1), baseClass.superSubstitutor(clazz2)) match {
      case (Some(superSubst1), Some(superSubst2)) =>
        val tp = ScParameterizedType(baseClassDesignator,
          baseClass.getTypeParameters.map(TypeParameterType(_)).toSeq)
        val tp1 = superSubst1(tp).asInstanceOf[ScParameterizedType]
        val tp2 = superSubst2(tp).asInstanceOf[ScParameterizedType]
        val resTypeArgs = new ArrayBuffer[ScType]
        val wildcards = new ArrayBuffer[ScExistentialArgument]()
        for (i <- baseClass.getTypeParameters.indices) {
          val substed1 = tp1.typeArguments.apply(i)
          val substed2 = tp2.typeArguments.apply(i)
          resTypeArgs += (baseClass.getTypeParameters.apply(i) match {
            case scp: ScTypeParam if scp.isCovariant => if (depth > 0) lubInner(substed1, substed2, depth - 1, checkWeak) else Any
            case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2, checkWeak)
            case _ =>
              val (v, ex) = calcForTypeParamWithoutVariance(substed1, substed2, checkWeak, count = wildcards.length + 1)
              wildcards ++= ex
              v
          })
        }
        if (wildcards.isEmpty) ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq)
        else ScExistentialType(ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq))
      case _ => Any
    }
  }

  private def getLeastUpperClasses(leftClasses: Seq[ClassLike], rightClasses: Seq[ClassLike]): Array[(ClassLike, Int, Int)] = {
    val res = new ArrayBuffer[(ClassLike, Int, Int)]
    def addClass(baseClassToAdd: ClassLike, x: Int, y: Int): Unit = {
      var i = 0
      var break = false
      while (!break && i < res.length) {
        val alreadyFound = res(i)._1
        if (baseClassToAdd.isSameOrBaseClass(alreadyFound)) {
          break = true //todo: join them somehow?
        } else if (alreadyFound.isSameOrBaseClass(baseClassToAdd)) {
          res(i) = (baseClassToAdd, x, y)
          break = true
        }
        i = i + 1
      }
      if (!break) {
        res += ((baseClassToAdd, x, y))
      }
    }
    def checkClasses(leftClasses: Seq[ClassLike], baseIndex: Int = -1, visited: mutable.HashSet[PsiElement] = mutable.HashSet.empty): Unit = {
      ProgressManager.checkCanceled()

      if (leftClasses.isEmpty) return
      val leftIterator = leftClasses.iterator
      var i = 0
      while (leftIterator.hasNext) {
        val leftClass = leftIterator.next()
        val rightIterator = rightClasses.iterator
        var break = false
        var j = 0
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
}

object ScalaBounds {

  sealed trait BoundKind {
    def inverse: BoundKind
  }

  object BoundKind {
    final case object Lub extends BoundKind {
      override def inverse: BoundKind = Glb
    }

    final case object Glb extends BoundKind {
      override def inverse: BoundKind = Lub
    }
  }
}