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
import result.TypingContext
import api.base.{ScStableCodeReferenceElement, ScPathElement}
import resolve.ScalaResolveResult
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiPackage, PsiTypeParameterListOwner, PsiNamedElement}
import api.toplevel.typedef.ScClass
import caches.CachesUtil

case class ScDesignatorType(val element: PsiNamedElement) extends ValueType {
  private var isStaticClass = false
  def isStatic = isStaticClass
  def this(elem: PsiNamedElement, isStaticClass: Boolean) {
    this(elem)
    this.isStaticClass = isStaticClass
  }
}

import _root_.scala.collection.immutable.{Map, HashMap}
import com.intellij.psi.{PsiTypeParameter, PsiClass}

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
        Some(new ScParameterizedType(new ScDesignatorType(arrayClass), Seq(arg)))
      } else None
    } else None
  }

  override def removeAbstracts = JavaArrayType(arg.removeAbstracts)
}

case class ScParameterizedType(designator : ScType, typeArgs : Seq[ScType]) extends ValueType {
  def designated: Option[PsiNamedElement] = ScType.extractDesignated(designator) match {
    case Some((e, _)) => Some(e)
    case _ => None
  }

  lazy val substitutor : ScSubstitutor = {
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
      case ScPolymorphicType(_, args, _, _) => {
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

  def getTupleType: Option[ScTupleType] = {
    ScType.extractClass(designator) match {
      case Some(clazz) if Option(clazz.getQualifiedName).exists(_.startsWith("scala.Tuple")) && typeArgs.length > 0 => {
        Some(new ScTupleType(typeArgs, clazz.getProject))
      }
      case _ => None
    }
  }

  def getFunctionType: Option[ScFunctionType] = {
    ScType.extractClass(designator) match {
      case Some(clazz) if clazz.getQualifiedName != null &&
              clazz.getQualifiedName.startsWith("scala.Function") && typeArgs.length > 0 => {
        Some(new ScFunctionType(typeArgs.apply(typeArgs.length - 1), typeArgs.slice(0, typeArgs.length - 1),
          clazz.getProject, clazz.getResolveScope))
      }
      case _ => None
    }
  }

  override def removeAbstracts = ScParameterizedType(designator.removeAbstracts, typeArgs.map(_.removeAbstracts))
}

object ScParameterizedType {
  def create(c: PsiClass, s : ScSubstitutor) =
    new ScParameterizedType(new ScDesignatorType(c), collection.immutable.Seq(c.getTypeParameters.map({
      tp => s subst(ScalaPsiManager.typeVariable(tp))
    }).toSeq : _*))
}

abstract case class ScPolymorphicType(name : String, args : List[ScTypeParameterType],
                                     lower : Suspension[ScType], upper : Suspension[ScType]) extends ValueType

case class ScTypeConstructorType(alias : ScTypeAliasDefinition, override val args : List[ScTypeParameterType],
                                 aliased : Suspension[ScType])
extends ScPolymorphicType(alias.name, args, aliased, aliased) {

  def this(tad : ScTypeAliasDefinition, s : ScSubstitutor) =
    this(tad, tad.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(tad.aliasedType(TypingContext.empty).getOrElse(Any))}))
}

case class ScTypeAliasType(alias : ScTypeAlias, override val args : List[ScTypeParameterType],
                           override val lower : Suspension[ScType], override val upper : Suspension[ScType])
extends ScPolymorphicType(alias.name, args, lower, upper) {

  def this(ta : ScTypeAlias, s : ScSubstitutor) =
    this(ta, ta.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(ta.lowerBound.getOrElse(Nothing))}),
      new Suspension[ScType]({() => s.subst(ta.upperBound.getOrElse(Any))}))
}

case class ScTypeParameterType(override val name: String, override val args: List[ScTypeParameterType],
                              override val lower: Suspension[ScType], override val upper: Suspension[ScType],
                              param: PsiTypeParameter)
extends ScPolymorphicType(name, args, lower, upper) {
  def this(tp : ScTypeParam, s : ScSubstitutor) =
    this(tp.name, tp.typeParameters.toList.map{new ScTypeParameterType(_, s)},
      new Suspension[ScType]({() => s.subst(tp.lowerBound.getOrElse(Nothing))}),
      new Suspension[ScType]({() => s.subst(tp.upperBound.getOrElse(Any))}),
      tp)

  def this(ptp: PsiTypeParameter, s: ScSubstitutor) =
    this(ptp.getName, ptp.getTypeParameters.toList.map(new ScTypeParameterType(_, s)),
      new Suspension[ScType]({() =>
              s.subst(
        ScCompoundType(collection.immutable.Seq(ptp.getExtendsListTypes.map(ScType.create(_, ptp.getProject)).toSeq ++
                ptp.getImplementsListTypes.map(ScType.create(_, ptp.getProject)).toSeq: _*), Seq.empty, Seq.empty, ScSubstitutor.empty))
      }),
      new Suspension[ScType]({() =>
              s.subst(
        ScCompoundType(ptp.getSuperTypes.map(ScType.create(_, ptp.getProject)).toSeq, Seq.empty, Seq.empty, ScSubstitutor.empty))
      }),
      ptp
    )

  def getId: String = {
    ScalaPsiUtil.getPsiElementId(param)
  }
}

case class ScUndefinedType(val tpt: ScTypeParameterType) extends NonValueType {
  var level = 0
  def this(tpt: ScTypeParameterType, level: Int) {
    this(tpt)
    this.level = level
  }

  def inferValueType: ValueType = tpt
}

case class ScAbstractType(val tpt: ScTypeParameterType, lower: ScType, upper: ScType) extends NonValueType {
  def inferValueType = tpt

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def removeAbstracts = simplifyType
}

private[types] object CyclicHelper {
  def compute[R](pn1: PsiNamedElement, pn2: PsiNamedElement)(fun: () => R): Option[R] = {
    val currentThread = Thread.currentThread
    def setup(pn1: PsiNamedElement, pn2: PsiNamedElement): Boolean = {
      var userData = pn1.getUserData(CachesUtil.CYCLIC_HELPER_KEY)
      var searches: List[PsiNamedElement] = if (userData == null) null else userData.getOrElse(currentThread, null)
      if (searches != null && searches.find(_ == pn2) == None)
        pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, userData.-(currentThread).
                +(currentThread -> (pn2 :: searches)))
      else if (searches == null)
        pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, Map(currentThread -> List(pn2)))
      else return false
      true
    }

    def close(pn1: PsiNamedElement, pn2: PsiNamedElement) = {
      var userData = pn1.getUserData(CachesUtil.CYCLIC_HELPER_KEY)
      var searches = userData.getOrElse(currentThread, null)
      if (searches != null && searches.length > 0)
        pn1.putUserData(CachesUtil.CYCLIC_HELPER_KEY, userData.-(currentThread).
                +(currentThread -> searches.tail))
      else {} //do nothing
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