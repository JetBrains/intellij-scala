package org.jetbrains.plugins.scala
package lang
package psi
package types

/**
 * @author ilyas
 */

import api.statements.{ScTypeAliasDefinition, ScTypeAlias}
import api.toplevel.{ScTypeParametersOwner}
import api.statements.params.ScTypeParam
import nonvalue.NonValueType
import psi.impl.ScalaPsiManager
import api.base.{ScStableCodeReferenceElement, ScPathElement}
import lang.resolve.ScalaResolveResult
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import caches.CachesUtil



import com.intellij.psi._
import collection.immutable.{::, Map, HashMap}
import result.{Success, TypingContext}
import api.toplevel.typedef.{ScTypeDefinition, ScClass}

case class JavaArrayType(arg: ScType) extends ValueType {

  def getParameterizedType(project: Project, scope: GlobalSearchScope): Option[ScType] = {
    val arrayClasses = JavaPsiFacade.getInstance(project).findClasses("scala.Array", scope)
    var arrayClass: PsiClass = null
    for (clazz <- arrayClasses) {
      clazz match {
        case _: ScClass => arrayClass = clazz
        case _ =>
      }
    }
    if (arrayClass != null) {
      val tps = arrayClass.getTypeParameters
      if (tps.length == 1) {
        Some(new ScParameterizedType(ScType.designator(arrayClass), Seq(arg)))
      } else None
    } else None
  }

  override def removeAbstracts = JavaArrayType(arg.removeAbstracts)

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case JavaArrayType(arg2) => Equivalence.equivInner (arg, arg2, uSubst, falseUndef)
      case ScParameterizedType(des, args) if args.length == 1 => {
        ScType.extractClass(des) match {
          case Some(td) if td.getQualifiedName == "scala.Array" => Equivalence.equivInner(arg, args(0), uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      }
      case _ => (false, uSubst)
    }
  }
}

case class ScParameterizedType(designator : ScType, typeArgs : Seq[ScType]) extends ValueType {
  def designated: Option[PsiNamedElement] = ScType.extractDesignated(designator) match {
    case Some((e, _)) => Some(e)
    case _ => None
  }

  @volatile
  private var sub: ScSubstitutor = null

  def substitutor: ScSubstitutor = {
    var res = sub
    if (res != null) return res
    res = substitutorInner
    sub = res
    res
  }

  private def substitutorInner : ScSubstitutor = {
    def forParams[T](paramsIterator: Iterator[T], initial: ScSubstitutor, map: T => ScTypeParameterType): ScSubstitutor = {
      var res = initial
      val argsIterator = typeArgs.iterator
      while (paramsIterator.hasNext && argsIterator.hasNext) {
        val p1 = map(paramsIterator.next)
        val p2 = argsIterator.next
        res = res bindT ((p1.name, p1.getId), p2)
      }
      res
    }
    designator match {
      case ScTypeParameterType(_, args, _, _, _) => {
        forParams(args.iterator, ScSubstitutor.empty, (p: ScTypeParameterType) => p)
      }
      case _ => ScType.extractDesignated(designator) match {
        case Some((owner: ScTypeParametersOwner, s)) => {
          forParams(owner.typeParameters.iterator, s, (tp: ScTypeParam) => ScalaPsiManager.typeVariable(tp))
        }
        case Some((owner: PsiTypeParameterListOwner, s)) => {
          forParams(owner.getTypeParameters.iterator, s, (ptp: PsiTypeParameter) => ScalaPsiManager.typeVariable(ptp))
        }
        case _ => ScSubstitutor.empty
      }
    }
  }

  override def removeAbstracts = ScParameterizedType(designator.removeAbstracts, typeArgs.map(_.removeAbstracts))

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    (this, r) match {
      case (ScParameterizedType(proj@ScProjectionType(projected, _, _), args), _) if proj.actualElement.isInstanceOf[ScTypeAliasDefinition] => {
        val a = proj.actualElement.asInstanceOf[ScTypeAliasDefinition]
        val subst = proj.actualSubst
        val lBound = subst.subst(a.lowerBound match {
          case Success(tp, _) => tp
          case _ => return (false, undefinedSubst)
        })
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        return Equivalence.equivInner(genericSubst.subst(lBound), r, undefinedSubst, falseUndef)
      }
      case (ScParameterizedType(ScDesignatorType(a: ScTypeAliasDefinition), args), _) => {
        val lBound = a.lowerBound match {
          case Success(tp, _) => tp
          case _ => return (false, undefinedSubst)
        }
        val genericSubst = ScalaPsiUtil.
                typesCallSubstitutor(a.typeParameters.map(tp => (tp.getName, ScalaPsiUtil.getPsiElementId(tp))), args)
        return Equivalence.equivInner(genericSubst.subst(lBound), r, undefinedSubst, falseUndef)
      }
      case (ScParameterizedType(designator, typeArgs), ScParameterizedType(designator1, typeArgs1)) => {
        var t = Equivalence.equivInner(designator, designator1, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        if (typeArgs.length != typeArgs1.length) return (false, undefinedSubst)
        val iterator1 = typeArgs.iterator
        val iterator2 = typeArgs1.iterator
        while (iterator1.hasNext && iterator2.hasNext) {
          t = Equivalence.equivInner(iterator1.next, iterator2.next, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        return (true, undefinedSubst)
      }
      case _ => return (false, undefinedSubst)
    }
  }

  def getTupleType: Option[ScTupleType] = {
    getStandardType("scala.Tuple") match {
      case Some((clazz, typeArgs)) if typeArgs.length > 0 =>
        Some(new ScTupleType(typeArgs)(clazz.getProject, clazz.getResolveScope))
      case _ => None
    }
  }

  def getFunctionType: Option[ScFunctionType] = {
    getStandardType("scala.Function") match {
      case Some((clazz, typeArgs)) if typeArgs.length > 0 =>
        val (params, Seq(ret)) = typeArgs.splitAt(typeArgs.length - 1)
        Some(new ScFunctionType(ret, params)(clazz.getProject, clazz.getResolveScope))
      case _ => None
    }
  }

  /**
   * @return Some((designator, paramType, returnType)), or None
   */
  def getPartialFunctionType: Option[(ScType, ScType, ScType)] = {
    getStandardType("scala.PartialFunction") match {
      case Some((_, Seq(param, ret))) => Some((designator, param, ret))
      case None => None
    }
  }

  /**
   * @param  prefix of the qualified name of the type
   * @return (typeDef, typeArgs)
   */
  private def getStandardType(prefix: String): Option[(ScTypeDefinition, Seq[ScType])] = {
    def startsWith(clazz: PsiClass, qualNamePrefix: String) = clazz.getQualifiedName != null && clazz.getQualifiedName.startsWith(qualNamePrefix)

    ScType.extractClassType(designator) match {
      case Some((clazz: ScTypeDefinition, sub)) if startsWith(clazz, prefix) =>
        val result = clazz.getType(TypingContext.empty)
        result match {
          case Success(t, _) =>
            val substituted = (sub followed substitutor).subst(t)
            substituted match {
              case pt: ScParameterizedType =>
                Some((clazz, pt.typeArgs))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }
}

object ScParameterizedType {
  def create(c: PsiClass, s : ScSubstitutor) =
    new ScParameterizedType(ScType.designator(c), collection.immutable.Seq(c.getTypeParameters.map({
      tp => s subst(ScalaPsiManager.typeVariable(tp))
    }).toSeq : _*))
}

case class ScTypeParameterType(name: String, args: List[ScTypeParameterType],
                              lower: Suspension[ScType], upper: Suspension[ScType],
                              param: PsiTypeParameter) extends ValueType {
  def this(ptp: PsiTypeParameter, s: ScSubstitutor) = {
    this(ptp match {case tp: ScTypeParam => tp.name case _ => ptp.getName},
         ptp match {case tp: ScTypeParam => tp.typeParameters.toList.map{new ScTypeParameterType(_, s)}
           case _ => ptp.getTypeParameters.toList.map(new ScTypeParameterType(_, s))},
         ptp match {case tp: ScTypeParam =>
             new Suspension[ScType]({() => s.subst(tp.lowerBound.getOrElse(Nothing))})
           case _ => new Suspension[ScType]({() => s.subst(
             ScCompoundType(collection.immutable.Seq(ptp.getExtendsListTypes.map(ScType.create(_, ptp.getProject)).toSeq ++
                   ptp.getImplementsListTypes.map(ScType.create(_, ptp.getProject)).toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty))
         })},
         ptp match {case tp: ScTypeParam =>
             new Suspension[ScType]({() => s.subst(tp.upperBound.getOrElse(Any))})
           case _ => new Suspension[ScType]({() => s.subst(
             ScCompoundType(ptp.getSuperTypes.map(ScType.create(_, ptp.getProject)).toSeq, Seq.empty, Seq.empty, ScSubstitutor.empty))
         })}, ptp)
  }

  def getId: String = {
    ScalaPsiUtil.getPsiElementId(param)
  }

  def isCovariant = {
    param match {
      case tp: ScTypeParam => tp.isCovariant
      case _ => false
    }
  }

  def isConravariant = {
    param match {
      case tp: ScTypeParam => tp.isContravariant
      case _ => false
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case stp: ScTypeParameterType => {
        if (r eq this) return (true, undefinedSubst)
        (CyclicHelper.compute(param, stp.param)(() => {
          val t = Equivalence.equivInner(lower.v, stp.lower.v, undefinedSubst, falseUndef)
          if (!t._1) (false, undefinedSubst)
          else {
            undefinedSubst = t._2
            Equivalence.equivInner(upper.v, stp.upper.v, undefinedSubst, falseUndef)
          }
        }) match {
          case None => (true, undefinedSubst)
          case Some(b) => b
        })
      }
      case _ => (false, undefinedSubst)
    }
  }
}



private[types] object CyclicHelper {
  def compute[R](pn1: PsiNamedElement, pn2: PsiNamedElement)(fun: () => R): Option[R] = {
    val currentThread = Thread.currentThread
    def setup(pn1: PsiNamedElement, pn2: PsiNamedElement): Boolean = {
      synchronized {
        var userData = pn1.getUserData(CachesUtil.CYCLIC_HELPER_KEY)
        var searches: List[PsiNamedElement] = if (userData == null) null else userData.getOrElse(currentThread, null)
        if (searches != null && searches.find(_ == pn2) == None)
          pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, userData.-(currentThread).
                  +(currentThread -> (pn2 :: searches)))
        else if (searches == null && userData != null)
          pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, userData.+(currentThread -> List(pn2)))
        else if (searches == null && userData == null)
          pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, Map(currentThread -> List(pn2)))
        else return false
        true
      }
    }

    def close(pn1: PsiNamedElement, pn2: PsiNamedElement) = {
      synchronized {
        var userData = pn1.getUserData(CachesUtil.CYCLIC_HELPER_KEY)
        var searches = userData.getOrElse(currentThread, null)
        if (searches != null && searches.length > 0)
          pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, userData.-(currentThread).
                  +(currentThread -> searches.tail))
        else {} //do nothing
      }
    }
    if (!setup(pn1, pn2)) return None
    if (pn1 != pn2 && !setup(pn2, pn1)) return None
    try {
      Some(fun.apply)
    }
    finally {
      close(pn1, pn2)
      if (pn1 != pn2) close(pn2, pn1)
    }
  }
}

case class ScTypeVariable(name: String) extends ValueType