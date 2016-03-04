package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter

import scala.collection.mutable

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(components: Seq[ScType], signatureMap: Map[Signature, ScType],
                          typesMap: Map[String, TypeAliasSignature]) extends ScalaType with ValueType {
  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = components.hashCode() + (signatureMap.hashCode() * 31 + typesMap.hashCode()) * 31
    }
    hash
  }


  def visitType(visitor: ScalaTypeVisitor) {
    visitor.visitCompoundType(this)
  }

  override def typeDepth: Int = {
    val depths = signatureMap.map {
      case (sign: Signature, tp: ScType) =>
        val rtDepth = tp.typeDepth
        if (sign.typeParams.nonEmpty) {
          (ScType.typeParamsDepth(sign.typeParams) + 1).max(rtDepth)
        } else rtDepth
    } ++ typesMap.map {
      case (s: String, sign: TypeAliasSignature) =>
        val boundsDepth = sign.lowerBound.typeDepth.max(sign.upperBound.typeDepth)
        if (sign.typeParams.nonEmpty) {
          (ScType.typeParamsDepth(sign.typeParams.toArray) + 1).max(boundsDepth)
        } else boundsDepth
    }
    val ints = components.map(_.typeDepth)
    val componentsDepth = if (ints.length == 0) 0 else ints.max
    if (depths.nonEmpty) componentsDepth.max(depths.max + 1)
    else componentsDepth
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts),
    signatureMap.map {
      case (s: Signature, tp: ScType) =>
        def updateTypeParam(tp: TypeParameter): TypeParameter = {
          new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), () => tp.lowerType().removeAbstracts,
            () => tp.upperType().removeAbstracts, tp.ptp)
        }

        val pTypes: List[Seq[() => ScType]] = s.substitutedTypes.map(_.map(f => () => f().removeAbstracts))
        val tParams: Array[TypeParameter] = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(updateTypeParam)
        val rt: ScType = tp.removeAbstracts
        (new Signature(s.name, pTypes, s.paramLength, tParams,
          ScSubstitutor.empty, s.namedElement match {
            case fun: ScFunction =>
              ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
            case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
            case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
            case named => named
          }, s.hasRepeatedParam), rt)
    }, typesMap.map {
      case (s: String, sign) => (s, sign.updateTypes(_.removeAbstracts))
    })

  import scala.collection.immutable.{HashSet => IHashSet}

  override def recursiveUpdate(update: ScType => (Boolean, ScType), visited: IHashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ =>
        def updateTypeParam(tp: TypeParameter): TypeParameter = {
          new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), {
            val res = tp.lowerType().recursiveUpdate(update, visited + this)
            () => res
          }, {
            val res = tp.upperType().recursiveUpdate(update, visited + this)
            () => res
          }, tp.ptp)
        }
        new ScCompoundType(components.map(_.recursiveUpdate(update, visited + this)), signatureMap.map {
          case (s: Signature, tp) =>

            val pTypes: List[Seq[() => ScType]] =
              s.substitutedTypes.map(_.map(f => () => f().recursiveUpdate(update, visited + this)))
            val tParams: Array[TypeParameter] = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(updateTypeParam)
            val rt: ScType = tp.recursiveUpdate(update, visited + this)
            (new Signature(
              s.name, pTypes, s.paramLength, tParams, ScSubstitutor.empty, s.namedElement match {
                case fun: ScFunction =>
                  ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
                case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
                case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
                case named => named
              }, s.hasRepeatedParam
            ), rt)
        }, typesMap.map {
          case (s, sign) => (s, sign.updateTypes(_.recursiveUpdate(update, visited + this)))
        })
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType = {
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        def updateTypeParam(tp: TypeParameter): TypeParameter = {
          new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), {
            val res = tp.lowerType().recursiveVarianceUpdateModifiable(newData, update, 1)
            () => res
          }, {
            val res = tp.upperType().recursiveVarianceUpdateModifiable(newData, update, 1)
            () => res
          }, tp.ptp)
        }
        new ScCompoundType(components.map(_.recursiveVarianceUpdateModifiable(newData, update, variance)), signatureMap.map {
          case (s: Signature, tp) =>
            val tParams = if (s.typeParams.length == 0) TypeParameter.EMPTY_ARRAY else s.typeParams.map(updateTypeParam)
            (new Signature(
            s.name, s.substitutedTypes.map(_.map(f => () => f().recursiveVarianceUpdateModifiable(newData, update, 1))),
            s.paramLength, tParams, ScSubstitutor.empty, s.namedElement, s.hasRepeatedParam
          ), tp.recursiveVarianceUpdateModifiable(newData, update, 1))
        }, typesMap.map {
          case (s, sign) => (s, sign.updateTypes(_.recursiveVarianceUpdateModifiable(newData, update, 1)))
        })
    }
  }

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case r: ScCompoundType =>
        if (r == this) return (true, undefinedSubst)
        if (components.length != r.components.length) return (false, undefinedSubst)
        val list = components.zip(r.components)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = Equivalence.equivInner(w1, w2, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
        }

        if (signatureMap.size != r.signatureMap.size) return (false, undefinedSubst)

        val iterator2 = signatureMap.iterator
        while (iterator2.hasNext) {
          val (sig, t) = iterator2.next()
          r.signatureMap.get(sig) match {
            case None => return (false, undefinedSubst)
            case Some(t1) =>
              val f = Equivalence.equivInner(t, t1, undefinedSubst, falseUndef)
              if (!f._1) return (false, undefinedSubst)
              undefinedSubst = f._2
          }
        }

        val types1 = typesMap
        val types2 = r.typesMap
        if (types1.size != types2.size) (false, undefinedSubst)
        else {
          val types1iterator = types1.iterator
          while (types1iterator.hasNext) {
            val (name, bounds1) = types1iterator.next()
            types2.get(name) match {
              case None => return (false, undefinedSubst)
              case Some (bounds2) =>
                var t = Equivalence.equivInner(bounds1.lowerBound, bounds2.lowerBound, undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = Equivalence.equivInner(bounds1.upperBound, bounds2.upperBound, undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
            }
          }
          (true, undefinedSubst)
        }
      case _ =>
        if (signatureMap.size == 0 && typesMap.size == 0) {
          val filtered = components.filter {
            case psi.types.Any => false
            case psi.types.AnyRef =>
              if (!r.conforms(psi.types.AnyRef)) return (false, undefinedSubst)
              false
            case ScDesignatorType(obj: PsiClass) if obj.qualifiedName == "java.lang.Object" =>
              if (!r.conforms(psi.types.AnyRef)) return (false, undefinedSubst)
              false
            case _ => true
          }
          if (filtered.length == 1) Equivalence.equivInner(filtered(0), r, undefinedSubst, falseUndef)
          else (false, undefinedSubst)
        } else (false, undefinedSubst)

    }
  }
}

object ScCompoundType {
  def fromPsi(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder], typeDecls: Seq[ScTypeAlias]): ScCompoundType = {
    val signatureMapVal: mutable.HashMap[Signature, ScType] = new mutable.HashMap[Signature, ScType] {
      override def elemHashCode(s : Signature) = s.name.hashCode * 31 + {
        val length = s.paramLength
        if (length.sum == 0) List(0).hashCode()
        else length.hashCode()
      }
    }

    for (decl <- decls) {
      decl match {
        case fun: ScFunction => signatureMapVal += ((Signature(fun), fun.returnType.getOrAny))
        case varDecl: ScVariable =>
          signatureMapVal ++= varDecl.declaredElements.map {
            e => (Signature.getter(e), e.getType().getOrAny)
          }
          signatureMapVal ++= varDecl.declaredElements.map {
            e => (Signature.setter(e), e.getType().getOrAny)
          }
        case valDecl: ScValue =>
          signatureMapVal ++= valDecl.declaredElements.map {
            e => (Signature.getter(e), e.getType().getOrAny)
          }
      }
    }

    ScCompoundType(
      components,
      signatureMapVal.toMap,
      typeDecls.map {
        typeDecl => (typeDecl.name, new TypeAliasSignature(typeDecl))
      }.toMap)
  }
}