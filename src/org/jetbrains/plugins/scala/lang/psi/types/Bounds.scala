package org.jetbrains.plugins.scala
package lang
package psi
package types

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import api.statements.params.ScTypeParam
import api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.PsiClass
import collection.mutable.{ArrayBuffer, Set, HashSet}
import api.statements.ScTypeAliasDefinition
import result.TypingContext
import com.intellij.psi.util.InheritanceUtil

object Bounds {
  private class Options(val tp: ScType) {
    val extract: Option[(PsiClass, ScSubstitutor)] = ScType.extractClassType(tp)
    def isEmpty = extract == None
    val projectionOption: Option[ScType] = ScType.projectionOption(tp)
    def getClazz: PsiClass = extract.get._1
    def getSubst: ScSubstitutor = extract.get._2
  }

  def glb(t1: ScType, t2: ScType, checkWeak: Boolean = false) = {
    if (t1.conforms(t2, checkWeak)) t1
    else if (t2.conforms(t1, checkWeak)) t2
    else new ScCompoundType(Seq(t1, t2), Seq.empty, Seq.empty, ScSubstitutor.empty)
  }

  def glb(typez: Seq[ScType], checkWeak: Boolean): ScType = {
    if (typez.length == 1) typez(0)
    var res = typez(0)
    for (i <- 1 until typez.length) {
      res = glb(res, typez(i), checkWeak)
    }
    res
  }

  def lub(t1: ScType, t2: ScType, checkWeak: Boolean = false): ScType = lub(t1, t2, 0, checkWeak)

  def weakLub(t1: ScType, t2: ScType): ScType = lub(t1, t2, checkWeak = true)

  private def lub(t1: ScType, t2: ScType, depth : Int, checkWeak: Boolean): ScType = {
    if (t1.conforms(t2, checkWeak)) t2
    else if (t2.conforms(t1, checkWeak)) t1
    else (t1, t2) match {
      case (fun@ScFunctionType(rt1, p1), ScFunctionType(rt2, p2)) if p1.length == p2.length =>
        new ScFunctionType(lub(rt1, rt2),
          collection.immutable.Seq(p1.toSeq.zip(p2.toSeq).map({case (t1, t2) => glb(t1, t2, checkWeak)}).toSeq: _*))(fun.getProject, fun.getScope)
      case (t1@ScTupleType(c1), ScTupleType(c2)) if c1.length == c2.length =>
        new ScTupleType(collection.immutable.Seq(c1.toSeq.zip(c2.toSeq).map({case (t1, t2) => lub(t1, t2, checkWeak)}).toSeq: _*))(t1.getProject, t1.getScope)

      case (ScSkolemizedType(_, Nil, _, upper), _) => lub(upper, t2)
      case (_, ScSkolemizedType(_, Nil, _, upper)) => lub(t1, upper)
      case (ScTypeParameterType(_, Nil, _, upper, _), _) => lub(upper.v, t2)
      case (_, ScTypeParameterType(_, Nil, _, upper, _)) => lub(t1, upper.v)
      case (ex : ScExistentialType, _) => lub(ex.skolem, t2)
      case (_, ex : ScExistentialType) => lub(t1, ex.skolem)
      case (_: ValType, _: ValType) => types.AnyVal
      case (JavaArrayType(arg1), JavaArrayType(arg2)) => {
        JavaArrayType(calcForTypeParamWithoutVariance(arg1, arg2))
      }
      case (JavaArrayType(arg), ScParameterizedType(des, args)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        ScParameterizedType(des, Seq(calcForTypeParamWithoutVariance(arg, args(0))))
      }
      case (ScParameterizedType(des, args), JavaArrayType(arg)) if args.length == 1 && (ScType.extractClass(des) match {
        case Some(q) => q.getQualifiedName == "scala.Array"
        case _ => false
      }) => {
        ScParameterizedType(des, Seq(calcForTypeParamWithoutVariance(arg, args(0))))
      }
      case _ => {

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
        if (aOptions.find(_.isEmpty) != None || bOptions.find(_.isEmpty) != None) Any
        else {
          val buf = new ArrayBuffer[ScType]
          val supers: Array[(Options, Int, Int)] =
            getLeastUpperClasses(aOptions, bOptions)
          for (sup <- supers) {
            val tp = getTypeForAppending(aOptions(sup._2), bOptions(sup._3), sup._1, depth, checkWeak)
            if (tp != Any) buf += tp
          }
          buf.toArray match {
            case a: Array[ScType] if a.length == 0 => Any
            case a: Array[ScType] if a.length == 1 => a(0)
            case many =>
              new ScCompoundType(collection.immutable.Seq(many.toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty)
          }
        }
        //todo: refinement for compound types
      }
    }
  }

  private def calcForTypeParamWithoutVariance(substed1: ScType, substed2: ScType): ScType = {
    if (substed1 equiv substed2) substed1 else {
      if (substed1 conforms substed2) {
        new ScExistentialArgument("_", List.empty, substed1, substed2)
      } else if (substed2 conforms substed1) {
        new ScExistentialArgument("_", List.empty, substed2, substed1)
      } else {
        new ScExistentialArgument("_", List.empty, new ScCompoundType(Seq(substed1, substed2), Seq.empty,
            Seq.empty, ScSubstitutor.empty), Any)
      }
    }
  }

  private def getTypeForAppending(clazz1: Options, clazz2: Options, baseClass: Options, depth: Int, checkWeak: Boolean): ScType = {
    val baseClassDesignator = {
      baseClass.projectionOption match {
        case Some(proj) => ScProjectionType(proj, baseClass.getClazz, ScSubstitutor.empty)
        case None => ScType.designator(baseClass.getClazz)
      }
    }
    if (baseClass.getClazz.getTypeParameters.length == 0) return baseClassDesignator
    (superSubstitutor(baseClass.getClazz, clazz1.getClazz, clazz1.getSubst),
            superSubstitutor(baseClass. getClazz, clazz2.getClazz, clazz2.getSubst)) match {
      case (Some(superSubst1), Some(superSubst2)) => {
        val tp = ScParameterizedType(baseClassDesignator, baseClass.getClazz.
                getTypeParameters.map(tp => ScalaPsiManager.instance(baseClass.getClazz.getProject).typeVariable(tp)))
        val tp1 = superSubst1.subst(tp).asInstanceOf[ScParameterizedType]
        val tp2 = superSubst2.subst(tp).asInstanceOf[ScParameterizedType]
        val resTypeArgs = new ArrayBuffer[ScType]
        for (i <- 0 until baseClass.getClazz.getTypeParameters.length) {
          val substed1 = tp1.typeArgs.apply(i)
          val substed2 = tp2.typeArgs.apply(i)
          resTypeArgs += (baseClass.getClazz.getTypeParameters.apply(i) match {
            case scp: ScTypeParam if scp.isCovariant => if (depth < 2) lub(substed1, substed2, depth + 1, checkWeak) else Any
            case scp: ScTypeParam if scp.isContravariant => glb(substed1, substed2, checkWeak)
            case _ => calcForTypeParamWithoutVariance(substed1, substed2)
          })
        }
        ScParameterizedType(baseClassDesignator, resTypeArgs.toSeq)
      }
      case _ => Any
    }
  }

  def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor) : Option[ScSubstitutor] = {
    //if (drv.isInheritor(base, true)) Some(ScSubstitutor.empty) else None
    superSubstitutor(base, drv, drvSubst, HashSet[PsiClass]())
  }

  private def superSubstitutor(base : PsiClass, drv : PsiClass, drvSubst : ScSubstitutor,
                               visited : Set[PsiClass]) : Option[ScSubstitutor] = {
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
          val st = iterator.next
          ScType.extractClassType(st) match {
            case None =>
            case Some((c, s)) => superSubstitutor(base, c, s, visited) match {
              case None =>
              case Some(s) => return Some(s.followed(drvSubst))
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
        res += Tuple(aClass, x, y)
      }
    }
    def checkClasses(aClasses: Seq[Options], baseIndex: Int = -1) {
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
          if (InheritanceUtil.isInheritorOrSelf(bClass.getClazz, aClass.getClazz, true)) {
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
            checkClasses(aClass.getClazz match {
              case t: ScTemplateDefinition => t.superTypes.map(tp => new Options(subst.subst(tp)))
              case p: PsiClass => p.getSupers.map(cl => new Options(ScType.designator(cl)))
            }, if (baseIndex == -1) i else baseIndex)
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