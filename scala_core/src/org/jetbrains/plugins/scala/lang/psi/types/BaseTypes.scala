package org.jetbrains.plugins.scala.lang.psi.types

import api.expr.ScThisReference
import com.intellij.psi.{PsiTypeParameter, PsiClass}
import api.statements.params.ScTypeParam
import api.toplevel.typedef.ScTypeDefinition
import _root_.scala.collection.mutable.{Set, HashMap, MultiMap}

object BaseTypes {
  def get(t : ScType) : Seq[ScType] = t match {
    case classT@ScDesignatorType(td : ScTypeDefinition) => Seq.single(classT) ++ reduce(td.superTypes)
    case classT@ScDesignatorType(c : PsiClass) => Seq.single(classT) ++ reduce(c.getSuperTypes.map{ScType.create(_, c.getProject)})
    case ScPolymorphicType(poly, s) => get(s.subst(poly.upperBound))
    case p : ScParameterizedType => p.designated match {
      case td : ScTypeDefinition => td.superTypes.map {p.substitutor.subst _}
      case clazz: PsiClass => {
        val s = p.substitutor
        clazz.getSuperTypes.map {t => s.subst(ScType.create(t, clazz.getProject))}
      }
      case _ => Seq.empty
    }
    case sin : ScSingletonType => get(sin.pathType)
    case ScExistentialType(q, wilds) => get(q).map{bt => ScExistentialTypeReducer.reduce(bt, wilds)}
    case ScCompoundType(comps, _, _) => reduce(comps)
    case proj@ScProjectionType(p, name) => proj.element match {
      case Some(td : ScTypeDefinition) => td.superTypes.map{seenFrom(_, td, p)}
      case Some(clazz : PsiClass) =>
        clazz.getSuperTypes.map{st => seenFrom(ScType.create(st, clazz.getProject), clazz, p)}
      case _ => Seq.empty
    }
  }

  def reduce (types : Seq[ScType]) : Seq[ScType] = {
    def extractClass (t : ScType) = t match {
      case ScDesignatorType(c : PsiClass) => Some(c)
      case ScParameterizedType(ScDesignatorType(c : PsiClass), _) => Some(c)
      case p : ScProjectionType => p.element match {
        case Some(c : PsiClass) => Some(c)
        case _ => None
      }
      case _ => None
    }

    val res = new HashMap[PsiClass, ScType]
    object all extends HashMap[PsiClass, Set[ScType]] with MultiMap[PsiClass, ScType]
    for (t <- types) {
      extractClass(t) match {
        case Some(c) => {
          val isBest = all.get(c) match {
            case None => true
            case Some(ts) => ts.find(t1 => !Conformance.conforms(t1, t)) == None
          }
          if (isBest) res += ((c, t))
          all.add(c, t)
        }
        case None => //not a class type
      }
    }
    res.values.toList
  }

  def seenFrom(t : ScType, c : PsiClass, s : ScType) : ScType = t match {
    case ScExistentialType(s1, wilds) => new ScExistentialType(seenFrom(t,c,s1), wilds)
    case ScPolymorphicType(tp : ScTypeParam, _) => {
      val (owner, i) = (tp.owner, tp.owner.typeParameters.indexOf(tp))
      for (bt <- get(s)) {
        bt match {
          case p@ScParameterizedType(ScDesignatorType(`owner`), _) => return p.substitutor.subst(tp)
          case _ =>
        }
      }
      t
    }
    case ScFunctionType(ret, params) => new ScFunctionType(seenFrom(ret, c, s), params.map{seenFrom(_, c, s)})
    case ScTupleType(comps) => new ScTupleType(comps.map{seenFrom(_, c, s)})
    case ScCompoundType(comps, decls, types) => new ScCompoundType(comps.map{seenFrom(_, c, s)}, decls, types)
    case ScProjectionType(p, name) => new ScProjectionType(seenFrom(p, c, s), name)
    case ScSingletonType(thisPath : ScThisReference) => {
      thisPath.refClass match {
        case Some(d) if isInheritorOrSelf(d, c) => ScType.extractClassType(s) match {
          case Some((e, _)) if isInheritorOrSelf(e, d) => s
          case _ => t
        }
        case _ => t
      }
    }
    case _ => ScType.extractClassType(t) match {
      case Some((c, _)) => {
        val cclazz = c.getContainingClass
        if (cclazz != null && isInheritorOrSelf(cclazz, c)) (ScType.extractClassType(s) match {
          case Some((e, _)) if isInheritorOrSelf(e, cclazz) => s
          case _ => t
        }) else t
      }
      case None => t
    }
  }

  private def isInheritorOrSelf(drv : PsiClass, base : PsiClass) = drv == base || drv.isInheritor(base, true)
}