package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import com.intellij.psi.PsiClass
import collection.mutable.ArrayBuffer
import api.statements.ScTypeAliasDefinition
import result.TypingContext
import com.intellij.psi.util.InheritanceUtil
import extensions.toPsiClassExt
import collection.mutable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

object Bounds {
  def lub(seq: Seq[ScType]): ScType = {
    seq.reduce((l: ScType, r: ScType) => lub(l,r))
  }

  private class Options(_tp: ScType) extends {
    val tp = _tp match {
      case ex: ScExistentialType => ex.skolem
      case tp => tp
    }
  } with AnyRef {
    val extract: Option[(PsiClass, ScSubstitutor)] = ScType.extractClassType(tp)
    def isEmpty = extract == None
    val projectionOption: Option[ScType] = ScType.projectionOption(tp)
    def getClazz: PsiClass = extract.get._1
    def getSubst: ScSubstitutor = extract.get._2
  }

  def glb(t1: ScType, t2: ScType, checkWeak: Boolean = false): ScType = {
    if (t1.conforms(t2, checkWeak)) t1
    else if (t2.conforms(t1, checkWeak)) t2
    else {
      (t1, t2) match {
        case (ScSkolemizedType(name, args, lower, upper), ScSkolemizedType(name2, args2, lower2, upper2)) =>
          ScSkolemizedType(name, args, lub(lower, lower2, checkWeak), glb(upper, upper2, checkWeak))
        case (ScSkolemizedType(name, args, lower, upper), _) => ScSkolemizedType(name, args, lub(lower, t2, checkWeak), glb(upper, t2))
        case (_, ScSkolemizedType(name, args, lower, upper)) => ScSkolemizedType(name, args, lub(lower, t1, checkWeak), glb(upper, t1))
        case (ex: ScExistentialType, _) => glb(ex.skolem, t2, checkWeak).unpackedType
        case (_, ex: ScExistentialType) => glb(t1, ex.skolem, checkWeak).unpackedType
        case _ => ScCompoundType(Seq(t1, t2), Seq.empty, Seq.empty, ScSubstitutor.empty)
      }
    }
  }


  def glb(typez: Seq[ScType], checkWeak: Boolean): ScType = {
    if (typez.length == 1) typez(0)
    var res = typez(0)
    for (i <- 1 until typez.length) {
      res = glb(res, typez(i), checkWeak)
    }
    res
  }

  def lub(t1: ScType, t2: ScType, checkWeak: Boolean = false): ScType = lub(t1, t2, 0, checkWeak)(stopAddingUpperBound = false)

  def weakLub(t1: ScType, t2: ScType): ScType = lub(t1, t2, checkWeak = true)

  private def lub(seq: Seq[ScType], depth : Int, checkWeak: Boolean)(implicit stopAddingUpperBound: Boolean): ScType = {
    seq.reduce((l: ScType, r: ScType) => lub(l,r, depth, checkWeak))
  }

  private def lub(t1: ScType, t2: ScType, depth : Int, checkWeak: Boolean)(implicit stopAddingUpperBound: Boolean): ScType = {
    if (t1.conforms(t2, checkWeak)) t2
    else if (t2.conforms(t1, checkWeak)) t1
    else {
      def lubWithExpandedAliases(t1: ScType, t2: ScType): ScType = {
        (t1, t2) match {
          case (ScDesignatorType(t: ScParameter), _) =>
            lub(t.getRealParameterType(TypingContext.empty).getOrAny, t2, 0, checkWeak)
          case (ScDesignatorType(t: ScTypedDefinition), _) if !t.isInstanceOf[ScObject] =>
            lub(t.getType(TypingContext.empty).getOrAny, t2, 0, checkWeak)
          case (_, ScDesignatorType(t: ScParameter)) =>
            lub(t1, t.getRealParameterType(TypingContext.empty).getOrAny, 0, checkWeak)
          case (_, ScDesignatorType(t: ScTypedDefinition)) if !t.isInstanceOf[ScObject] =>
            lub(t1, t.getType(TypingContext.empty).getOrAny, 0, checkWeak)
          case (ex: ScExistentialType, _) => lub(ex.skolem, t2, 0, checkWeak).unpackedType
          case (_, ex: ScExistentialType) => lub(t1, ex.skolem, 0, checkWeak).unpackedType
          case (ScTypeParameterType(_, Nil, _, upper, _), _) => lub(upper.v, t2, 0, checkWeak)
          case (_, ScTypeParameterType(_, Nil, _, upper, _)) => lub(t1, upper.v, 0, checkWeak)
          case (ScSkolemizedType(name, args, lower, upper), ScSkolemizedType(name2, args2, lower2, upper2)) =>
            ScSkolemizedType(name, args, glb(lower, lower2, checkWeak), lub(upper, upper2, 0, checkWeak))
          case (ScSkolemizedType(name, args, lower, upper), r) =>
            ScSkolemizedType(name, args, glb(lower, r, checkWeak), lub(upper, t2, 0, checkWeak))
          case (r, ScSkolemizedType(name, args, lower, upper)) =>
            ScSkolemizedType(name, args, glb(lower, r, checkWeak), lub(upper, t2, 0, checkWeak))
          case (_: ValType, _: ValType) => types.AnyVal
          case (JavaArrayType(arg1), JavaArrayType(arg2)) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg1, arg2, depth, checkWeak)
            ex match {
              case Some(w) => ScExistentialType(JavaArrayType(v), List(w))
              case None => JavaArrayType(v)
            }
          case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 && (ScType.extractClass(des) match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args(0), depth, checkWeak)
            ex match {
              case Some(w) => ScExistentialType(ScParameterizedType(des, Seq(v)), List(w))
              case None => ScParameterizedType(des, Seq(v))
            }
          case (ScParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (ScType.extractClass(des) match {
            case Some(q) => q.qualifiedName == "scala.Array"
            case _ => false
          }) =>
            val (v, ex) = calcForTypeParamWithoutVariance(arg, args(0), depth, checkWeak)
            ex match {
              case Some(w) => ScExistentialType(ScParameterizedType(des, Seq(v)), List(w))
              case None => ScParameterizedType(des, Seq(v))
            }
          case (JavaArrayType(_), tp) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case (tp, JavaArrayType(_)) =>
            if (tp.conforms(AnyRef)) AnyRef
            else Any
          case _ =>
            val aOptions: Seq[Options] = {
              t1 match {
                case ScCompoundType(comps1, decls1, typeDecls1, subst1) => comps1.map(new Options(_))
                case _ => Seq(new Options(t1))
              }
            }
            val bOptions: Seq[Options] = {
              t2 match {
                case ScCompoundType(comps1, decls1, typeDecls1, subst1) => comps1.map(new Options(_))
                case _ => Seq(new Options(t2))
              }
            }
            if (aOptions.exists(_.isEmpty) || bOptions.exists(_.isEmpty)) types.Any
            else {
              val buf = new ArrayBuffer[ScType]
              val supers: Array[(Options, Int, Int)] =
                getLeastUpperClasses(aOptions, bOptions)
              for (sup <- supers) {
                val tp = getTypeForAppending(aOptions(sup._2), bOptions(sup._3), sup._1, depth, checkWeak)
                if (tp != types.Any) buf += tp
              }
              buf.toArray match {
                case a: Array[ScType] if a.length == 0 => types.Any
                case a: Array[ScType] if a.length == 1 => a(0)
                case many =>
                  new ScCompoundType(collection.immutable.Seq(many.toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty)
              }
            }
            //todo: refinement for compound types
        }
      }
      lubWithExpandedAliases(ScType.expandAliases(t1).getOrElse(t1), ScType.expandAliases(t2).getOrElse(t2)).unpackedType
    }
  }

  private def calcForTypeParamWithoutVariance(substed1: ScType, substed2: ScType, depth: Int, checkWeak: Boolean, count: Int = 1)
                                             (implicit stopAddingUpperBound: Boolean): (ScType, Option[ScExistentialArgument]) = {
    if (substed1 equiv substed2) (substed1, None) else {
      if (substed1 conforms substed2) {
        (ScTypeVariable("_$" + count), Some(ScExistentialArgument("_$" + count, List.empty, substed1, substed2)))
      } else if (substed2 conforms substed1) {
        (ScTypeVariable("_$" + count), Some(ScExistentialArgument("_$" + count, List.empty, substed2, substed1)))
      } else {
        (substed1, substed2) match {
          case (ScSkolemizedType(name, args, lower, upper), ScSkolemizedType(name2, args2, lower2, upper2)) =>
            val newLub = if (stopAddingUpperBound) types.Any else lub(Seq(upper, upper2), 0, checkWeak)(stopAddingUpperBound = true)
            (ScSkolemizedType(name, args, glb(lower, lower2, checkWeak), newLub), None)
          case (ScSkolemizedType(name, args, lower, upper), _) =>
            val newLub = if (stopAddingUpperBound) types.Any else lub(Seq(upper, substed2), 0, checkWeak)(stopAddingUpperBound = true)
            (ScSkolemizedType(name, args, glb(lower, substed2), newLub), None)
          case (_, ScSkolemizedType(name, args, lower, upper)) =>
            val newLub = if (stopAddingUpperBound) types.Any else lub(Seq(upper, substed1), 0, checkWeak)(stopAddingUpperBound = true)
            (ScSkolemizedType(name, args, glb(lower, substed1), newLub), None)
          case _ =>
            val newGlb = Bounds.glb(substed1, substed2)
            if (!stopAddingUpperBound) {
              //don't calculate the lub of the types themselves, but of the components of their compound types (if existing)
              // example: the lub of "_ >: Int with Double <: AnyVal" & Long we need here should be AnyVal, not Any
              def getTypesForLubEvaluation(t: ScType) = Seq(t)
              val typesToCover = getTypesForLubEvaluation(substed1) ++ getTypesForLubEvaluation(substed2)
              val newLub = Bounds.lub(typesToCover, 0, checkWeak = false)(stopAddingUpperBound = true)
              (ScTypeVariable("_$" + count), Some(ScExistentialArgument("_$" + count, List.empty, newGlb, newLub)))
            } else {
              //todo: this is wrong, actually we should pick lub, just without merging parameters in this method
              (ScTypeVariable("_$" + count), Some(ScExistentialArgument("_$" + count, List.empty, newGlb, types.Any)))
            }
        }
      }
    }
  }

  private def getTypeForAppending(clazz1: Options, clazz2: Options, baseClass: Options, depth: Int, checkWeak: Boolean)
                                 (implicit stopAddingUpperBound: Boolean): ScType = {
    val baseClassDesignator = {
      baseClass.projectionOption match {
        case Some(proj) => ScProjectionType(proj, baseClass.getClazz, superReference = false)
        case None => ScType.designator(baseClass.getClazz)
      }
    }
    if (baseClass.getClazz.getTypeParameters.length == 0) return baseClassDesignator
    (superSubstitutor(baseClass.getClazz, clazz1.getClazz, clazz1.getSubst),
      superSubstitutor(baseClass.getClazz, clazz2.getClazz, clazz2.getSubst)) match {
      case (Some(superSubst1), Some(superSubst2)) => {
        val tp = ScParameterizedType(baseClassDesignator, baseClass.getClazz.
          getTypeParameters.map(tp => ScalaPsiManager.instance(baseClass.getClazz.getProject).typeVariable(tp)))
        val tp1 = superSubst1.subst(tp).asInstanceOf[ScParameterizedType]
        val tp2 = superSubst2.subst(tp).asInstanceOf[ScParameterizedType]
        val resTypeArgs = new ArrayBuffer[ScType]
        val wildcards = new ArrayBuffer[ScExistentialArgument]()
        for (i <- 0 until baseClass.getClazz.getTypeParameters.length) {
          val substed1 = tp1.typeArgs.apply(i)
          val substed2 = tp2.typeArgs.apply(i)
          resTypeArgs += (baseClass.getClazz.getTypeParameters.apply(i) match {
            case scp: ScTypeParam if scp.isCovariant => if (depth < 2) lub(substed1, substed2, depth + 1, checkWeak) else types.Any
            case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2, checkWeak)
            case _ =>
              val (v, ex) = calcForTypeParamWithoutVariance(substed1, substed2, depth, checkWeak, count = wildcards.length + 1)
              wildcards ++= ex
              v
          })
        }
        if (wildcards.isEmpty) ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq)
        else ScExistentialType(ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq), wildcards.toList)
      }
      case _ => types.Any
    }
  }

  def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor) : Option[ScSubstitutor] = {
    //if (drv.isInheritor(base, true)) Some(ScSubstitutor.empty) else None
    superSubstitutor(base, drv, drvSubst, mutable.HashSet[PsiClass]())
  }

  private def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor,
                               visited : mutable.Set[PsiClass]) : Option[ScSubstitutor] = {
    //todo: move somewhere and cache
    if (base == drv) Some(drvSubst) else {
      if (visited.contains(drv)) None else {
        visited += drv
        val superTypes: Seq[ScType] = drv match {
          case td: ScTemplateDefinition => td.superTypes
          case _ => drv.getSuperTypes.map{t => ScType.create(t, drv.getProject)}
        }
        val iterator = superTypes.iterator
        while(iterator.hasNext) {
          val st = iterator.next()
          ScType.extractClassType(st) match {
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

  def putAliases(template: ScTemplateDefinition, s: ScSubstitutor): ScSubstitutor = {
    var run = s
    for (alias <- template.aliases) {
      alias match {
        case aliasDef: ScTypeAliasDefinition if s.aliasesMap.get(aliasDef.name) == None =>
          run = run bindA (aliasDef.name, {() => aliasDef.aliasedType(TypingContext.empty).getOrAny})
        case _ =>
      }
    }
    run
  }

  private def getLeastUpperClasses(aClasses: Seq[Options], bClasses: Seq[Options]): Array[(Options, Int, Int)] = {
    val res = new ArrayBuffer[(Options, Int, Int)]
    def addClass(aClass: Options, x: Int, y: Int) {
      var i = 0
      var break = false
      while (!break && i < res.length) {
        val clazz = res(i)._1
        if (InheritanceUtil.isInheritorOrSelf(clazz.getClazz, aClass.getClazz, true)) {
          //todo: join them
          break = true
        } else if (ScalaPsiUtil.cachedDeepIsInheritor(aClass.getClazz, clazz.getClazz)) {
          res(i) = (aClass, x, y)
          break = true
        }
        i = i + 1
      }
      if (!break) {
        res += ((aClass, x, y))
      }
    }
    def checkClasses(aClasses: Seq[Options], baseIndex: Int = -1, visited: mutable.HashSet[PsiClass] = mutable.HashSet.empty) {
      if (aClasses.length == 0) return
      val aIter = aClasses.iterator
      var i = 0
      while (aIter.hasNext) {
        val aClass = aIter.next()
        val bIter = bClasses.iterator
        var break = false
        var j = 0
        while (!break && bIter.hasNext) {
          val bClass = bIter.next()
          val clazz = aClass.getClazz
          if (InheritanceUtil.isInheritorOrSelf(bClass.getClazz, clazz, true)) {
            addClass(aClass, if (baseIndex == -1) i else baseIndex, j)
            break = true
          } else {
            val subst = aClass.projectionOption match {
              case Some(proj) => {
                import collection.immutable.Map
                new ScSubstitutor(Map.empty, Map.empty, Some(proj))
              }
              case None => ScSubstitutor.empty
            }
            if (!visited.contains(clazz))
              checkClasses(clazz match {
                case t: ScTemplateDefinition => t.superTypes.map(tp => new Options(subst.subst(tp)))
                case p: PsiClass => p.getSupers.map(cl => new Options(ScType.designator(cl)))
              }, if (baseIndex == -1) i else baseIndex, visited + clazz)
          }
          j += 1
        }
        i += 1
      }
    }
    checkClasses(aClasses)
    res.toArray
  }
}