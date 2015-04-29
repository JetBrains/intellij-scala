package org.jetbrains.plugins.scala
package lang
package psi
package types

/**
 * @author ilyas
 */

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ConcurrentWeakHashMap
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.collection.immutable.{HashSet, ListMap, Map}

class JavaArrayType private (val arg: ScType) extends ValueType {
  override val hashCode: Int = arg.hashCode() + 1

  override def equals(other: scala.Any): Boolean = other match {
    case j: JavaArrayType => arg == j.arg
    case _ => false
  }

  override def toString: String = s"JavaArrayType($arg)"

  def getParameterizedType(project: Project, scope: GlobalSearchScope): Option[ScType] = {
    val arrayClasses = ScalaPsiManager.instance(project).getCachedClasses(scope, "scala.Array")
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
        Some(ScParameterizedType(ScType.designator(arrayClass), Seq(arg)))
      } else None
    } else None
  }

  override def removeAbstracts = JavaArrayType(arg.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        JavaArrayType(arg.recursiveUpdate(update, visited + this))
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        JavaArrayType(arg.recursiveVarianceUpdateModifiable(newData, update, 0))
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case JavaArrayType(arg2) => Equivalence.equivInner (arg, arg2, uSubst, falseUndef)
      case ScParameterizedType(des, args) if args.length == 1 =>
        ScType.extractClass(des) match {
          case Some(td) if td.qualifiedName == "scala.Array" => Equivalence.equivInner(arg, args(0), uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case _ => (false, uSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitJavaArrayType(this)
  }

  override def typeDepth: Int = arg.typeDepth
}

object JavaArrayType {
  def apply(arg: ScType): JavaArrayType = {
    val result = new JavaArrayType(arg)
    ScType.allTypesCache.intern(result).asInstanceOf[JavaArrayType]
  }

  def unapply(j: JavaArrayType): Option[ScType] = {
    Some(j.arg)
  }
}

class ScParameterizedType private (val designator : ScType, val typeArgs : Seq[ScType]) extends ValueType {
  override protected def isAliasTypeInner: Option[AliasType] = {
    this match {
      case ScParameterizedType(ScDesignatorType(ta: ScTypeAlias), args) =>
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))), args)
        Some(AliasType(ta, ta.lowerBound.map(genericSubst.subst), ta.upperBound.map(genericSubst.subst)))
      case ScParameterizedType(p: ScProjectionType, args) if p.actualElement.isInstanceOf[ScTypeAlias] =>
        val ta: ScTypeAlias = p.actualElement.asInstanceOf[ScTypeAlias]
        val subst: ScSubstitutor = p.actualSubst
        val genericSubst = ScalaPsiUtil.
          typesCallSubstitutor(ta.typeParameters.map(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp))), args)
        val s = subst.followed(genericSubst)
        Some(AliasType(ta, ta.lowerBound.map(s.subst), ta.upperBound.map(s.subst)))
      case _ => None
    }
  }

  override val hashCode: Int = 2 + designator.hashCode() + typeArgs.hashCode() * 31

  def substitutor: ScSubstitutor = {
    val res = ScParameterizedType.substitutorCache.get(this)
    if (res == null) {
      val res = substitutorInner
      ScParameterizedType.substitutorCache.put(this, res)
      res
    } else res
  }

  private def substitutorInner : ScSubstitutor = {
    def forParams[T](paramsIterator: Iterator[T], initial: ScSubstitutor, map: T => ScTypeParameterType): ScSubstitutor = {
      val argsIterator = typeArgs.iterator
      val builder = ListMap.newBuilder[(String, String), ScType]
      while (paramsIterator.hasNext && argsIterator.hasNext) {
        val p1 = map(paramsIterator.next())
        val p2 = argsIterator.next()
        builder += (((p1.name, p1.getId), p2))
        //res = res bindT ((p1.name, p1.getId), p2)
      }
      val subst = new ScSubstitutor(builder.result(), Map.empty, None)
      initial followed subst
    }
    designator match {
      case ScTypeParameterType(_, args, _, _, _) =>
        forParams(args.iterator, ScSubstitutor.empty, (p: ScTypeParameterType) => p)
      case _ => ScType.extractDesignated(designator, withoutAliases = false) match {
        case Some((owner: ScTypeParametersOwner, s)) =>
          forParams(owner.typeParameters.iterator, s, (tp: ScTypeParam) => ScalaPsiManager.typeVariable(tp))
        case Some((owner: PsiTypeParameterListOwner, s)) =>
          forParams(owner.getTypeParameters.iterator, s, (ptp: PsiTypeParameter) => ScalaPsiManager.typeVariable(ptp))
        case _ => ScSubstitutor.empty
      }
    }
  }

  override def removeAbstracts = ScParameterizedType(designator.removeAbstracts, typeArgs.map(_.removeAbstracts))

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    val newVisited = visited + this
    update(this) match {
      case (true, res) => res
      case _ =>
        ScParameterizedType(designator.recursiveUpdate(update, newVisited), typeArgs.map(_.recursiveUpdate(update, newVisited)))
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                           variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        val des = ScType.extractDesignated(designator, withoutAliases = false) match {
          case Some((n: ScTypeParametersOwner, _)) =>
            n.typeParameters.map {
              case tp if tp.isContravariant => -1
              case tp if tp.isCovariant => 1
              case _ => 0
            }
          case _ => Seq.empty
        }
        ScParameterizedType(designator.recursiveVarianceUpdateModifiable(newData, update, variance),
          typeArgs.zipWithIndex.map {
            case (ta, i) =>
              val v = if (i < des.length) des(i) else 0
              ta.recursiveVarianceUpdateModifiable(newData, update, v * variance)
          })
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    (this, r) match {
      case (ScParameterizedType(ScAbstractType(tpt, lower, upper), args), _) =>
        if (falseUndef) return (false, uSubst)
        val subst = new ScSubstitutor(Map(tpt.args.zip(args).map {
          case (tpt: ScTypeParameterType, tp: ScType) =>
            ((tpt.param.name, ScalaPsiUtil.getPsiElementId(tpt.param)), tp)
        }: _*), Map.empty, None)
        var t: (Boolean, ScUndefinedSubstitutor) = Conformance.conformsInner(subst.subst(upper), r, Set.empty, uSubst)
        if (!t._1) return (false, uSubst)
        t = Conformance.conformsInner(r, subst.subst(lower), Set.empty, t._2)
        if (!t._1) return (false, uSubst)
        (true, t._2)
      case (ScParameterizedType(proj@ScProjectionType(projected, _, _), args), _) if proj.actualElement.isInstanceOf[ScTypeAliasDefinition] =>
        isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            Equivalence.equivInner(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, r, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case (ScParameterizedType(ScDesignatorType(a: ScTypeAliasDefinition), args), _) =>
        isAliasType match {
          case Some(AliasType(ta: ScTypeAliasDefinition, lower, _)) =>
            Equivalence.equivInner(lower match {
              case Success(tp, _) => tp
              case _ => return (false, uSubst)
            }, r, uSubst, falseUndef)
          case _ => (false, uSubst)
        }
      case (ScParameterizedType(_, _), ScParameterizedType(designator1, typeArgs1)) =>
        var t = Equivalence.equivInner(designator, designator1, undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        if (typeArgs.length != typeArgs1.length) return (false, undefinedSubst)
        val iterator1 = typeArgs.iterator
        val iterator2 = typeArgs1.iterator
        while (iterator1.hasNext && iterator2.hasNext) {
          t = Equivalence.equivInner(iterator1.next(), iterator2.next(), undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }
        (true, undefinedSubst)
      case _ => (false, undefinedSubst)
    }
  }

  /**
   * @return Some((designator, paramType, returnType)), or None
   */
  def getPartialFunctionType: Option[(ScType, ScType, ScType)] = {
    getStandardType("scala.PartialFunction") match {
      case Some((typeDef, Seq(param, ret))) => Some((ScDesignatorType(typeDef), param, ret))
      case None => None
    }
  }

  /**
   * @param  prefix of the qualified name of the type
   * @return (typeDef, typeArgs)
   */
  private def getStandardType(prefix: String): Option[(ScTypeDefinition, Seq[ScType])] = {
    def startsWith(clazz: PsiClass, qualNamePrefix: String) = clazz.qualifiedName != null && clazz.qualifiedName.startsWith(qualNamePrefix)

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

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitParameterizedType(this)
  }

  override def typeDepth: Int = {
    val depths = typeArgs.map(_.typeDepth)
    if (depths.length == 0) designator.typeDepth //todo: shouldn't be possible
    else designator.typeDepth.max(depths.max + 1)
  }

  override def isFinalType: Boolean = designator.isFinalType && !typeArgs.exists {
    case tp: ScTypeParameterType => tp.isConravariant || tp.isCovariant
    case _ => false
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ScParameterizedType]

  override def equals(other: Any): Boolean = other match {
    case that: ScParameterizedType =>
      (that canEqual this) &&
        designator == that.designator &&
        typeArgs == that.typeArgs
    case _ => false
  }
}

object ScParameterizedType {
  val substitutorCache: ConcurrentWeakHashMap[ScParameterizedType, ScSubstitutor] = new ConcurrentWeakHashMap()

  def apply(designator: ScType, typeArgs: Seq[ScType]): ValueType = {
    val simpleParameterizedType = new ScParameterizedType(designator, typeArgs)

    val result = designator match {
      case ScProjectionType(_: ScCompoundType, _, _) =>
        simpleParameterizedType.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, _, upper)) => upper.getOrElse(simpleParameterizedType) match {
            case v: ValueType => v
            case _ => simpleParameterizedType
          }
          case _ => simpleParameterizedType
        }
      case _ => simpleParameterizedType
    }
    ScType.allTypesCache.intern(result).asInstanceOf[ValueType]
  }

  def unapply(p: ScParameterizedType): Option[(ScType, Seq[ScType])] = {
    Some(p.designator, p.typeArgs)
  }
}

class ScTypeParameterType private (val name: String, val args: List[ScTypeParameterType],
                                   val lower: Suspension[ScType], val upper: Suspension[ScType],
                                   val param: PsiTypeParameter) extends ValueType {
  override val hashCode: Int = 3 + (param.hashCode() * 31 + args.hashCode()) * 31 + name.hashCode

  override def equals(other: Any): Boolean = other match {
    case that: ScTypeParameterType =>
      name == that.name &&
        args == that.args &&
        param == that.param
    case _ => false
  }

  override def toString: String = s"ScTypeParameterType($name, $args, $lower, $upper, $param)"

  private def this(ptp: PsiTypeParameter, s: ScSubstitutor) = {
    this(ptp match {case tp: ScTypeParam => tp.name case _ => ptp.name},
      ptp match {case tp: ScTypeParam => tp.typeParameters.toList.map{ScTypeParameterType(_, s)}
      case _ => ptp.getTypeParameters.toList.map(ScTypeParameterType(_, s))},
      ptp match {case tp: ScTypeParam =>
        new Suspension[ScType]({() => s.subst(tp.lowerBound.getOrNothing)})
      case _ => new Suspension[ScType]({() => s.subst(
        ScCompoundType(ptp.getExtendsListTypes.map(ScType.create(_, ptp.getProject)).toSeq ++
          ptp.getImplementsListTypes.map(ScType.create(_, ptp.getProject)).toSeq, Map.empty, Map.empty))
      })},
      ptp match {case tp: ScTypeParam =>
        new Suspension[ScType]({() => s.subst(tp.upperBound.getOrAny)})
      case _ => new Suspension[ScType]({() => s.subst(
        ScalaPsiManager.instance(ptp.getProject).psiTypeParameterUpperType(ptp))})}, ptp)
  }

  @volatile
  private var id: String = null
  def getId: String = {
    if (id == null) {
      id = ScalaPsiUtil.getPsiElementId(param)
    }
    id
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
    val undefinedSubst = uSubst
    r match {
      case stp: ScTypeParameterType =>
        if (stp.param eq param) (true, undefinedSubst)
        else (false, undefinedSubst)
      case _ => (false, undefinedSubst)
    }
  }

  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitTypeParameterType(this)
  }
}

object ScTypeParameterType {
  def toTypeParameterType(tp: TypeParameter): ScTypeParameterType = {
    ScTypeParameterType(tp.name, tp.typeParams.map(toTypeParameterType).toList, new Suspension[ScType](tp.lowerType()),
      new Suspension[ScType](tp.upperType()), tp.ptp)
  }

  def apply(name: String, args: List[ScTypeParameterType],
            lower: Suspension[ScType], upper: Suspension[ScType],
            param: PsiTypeParameter): ScTypeParameterType = {
    val result = new ScTypeParameterType(name, args, lower, upper, param)
    ScType.allTypesCache.intern(result).asInstanceOf[ScTypeParameterType]
  }

  def apply(ptp: PsiTypeParameter, s: ScSubstitutor) : ScTypeParameterType = {
    val result = new ScTypeParameterType(ptp, s)
    ScType.allTypesCache.intern(result).asInstanceOf[ScTypeParameterType]
  }

  def unapply(t: ScTypeParameterType): Option[(String, List[ScTypeParameterType], Suspension[ScType], Suspension[ScType], PsiTypeParameter)] = {
    Some(t.name, t.args, t.lower, t.upper, t.param)
  }
}



private[types] object CyclicHelper {
  def compute[R](pn1: PsiNamedElement, pn2: PsiNamedElement)(fun: () => R): Option[R] = {
    import org.jetbrains.plugins.scala.caches.ScalaRecursionManager._
    doComputationsForTwoElements(pn1, pn2, (p: Object, searches: Seq[Object]) => {
      !searches.contains(p)
    }, pn2, pn1, fun(), CYCLIC_HELPER_KEY)
  }
}

class ScTypeVariable private (val name: String) extends ValueType {
  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitTypeVariable(this)
  }

  override def equals(other: scala.Any): Boolean = other match {
    case tv: ScTypeVariable => name == tv.name
    case _ => false
  }

  override val hashCode: Int = 4 + name.hashCode

  override def toString: String = s"TypeVariable($name)"

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    r match {
      case ScTypeVariable(`name`) => (true, uSubst)
      case _ => (false, uSubst)
    }
  }
}

object ScTypeVariable {
  def apply(name: String): ScTypeVariable = {
    val result = new ScTypeVariable(name)
    ScType.allTypesCache.intern(result).asInstanceOf[ScTypeVariable]
  }

  def unapply(t: ScTypeVariable): Option[String] = {
    Some(t.name)
  }
}