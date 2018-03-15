package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, TypeParametersArrayExt, TypeVisitor, ValueType, _}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{ScSubstitutor, Update}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(components: Seq[ScType],
                          signatureMap: Map[Signature, ScType] = Map.empty,
                          typesMap: Map[String, TypeAliasSignature] = Map.empty)
                         (implicit override val projectContext: ProjectContext)

  extends ScalaType with ValueType {

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1)
      hash = Objects.hash(components, signatureMap, typesMap)

    hash
  }


  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case scalaVisitor: ScalaTypeVisitor => scalaVisitor.visitCompoundType(this)
    case _ =>
  }

  override def typeDepth: Int = {
    val depths = signatureMap.map {
      case (sign: Signature, tp: ScType) =>
        tp.typeDepth
          .max(sign.typeParams.depth)
    } ++ typesMap.map {
      case (_: String, TypeAliasSignature(_, params, lowerBound, upperBound, _, _)) =>
        lowerBound.typeDepth
          .max(upperBound.typeDepth)
          .max(params.toArray.depth)
    }
    val ints = components.map(_.typeDepth)
    val componentsDepth = if (ints.isEmpty) 0 else ints.max
    if (depths.nonEmpty) componentsDepth.max(depths.max + 1)
    else componentsDepth
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts),
    signatureMap.map {
      case (s: Signature, tp: ScType) =>
        val pTypes: Seq[Seq[() => ScType]] = s.substitutedTypes.map(_.map(f => () => f().removeAbstracts))
        val tParams = s.typeParams.update(_.removeAbstracts)
        val rt: ScType = tp.removeAbstracts
        (new Signature(s.name, pTypes, tParams,
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

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): ScCompoundType = {
    new ScCompoundType(components.map(_.recursiveUpdateImpl(updates, visited)), signatureMap.map {
      case (s: Signature, tp) =>

        val pTypes: Seq[Seq[() => ScType]] =
          s.substitutedTypes.map(_.map(f => () => f().recursiveUpdateImpl(updates, visited, isLazySubtype = true)))
        val tParams = s.typeParams.update(_.recursiveUpdateImpl(updates, visited, isLazySubtype = true))
        val rt: ScType = tp.recursiveUpdateImpl(updates, visited)
        (new Signature(
          s.name, pTypes, tParams, ScSubstitutor.empty, s.namedElement match {
            case fun: ScFunction =>
              ScFunction.getCompoundCopy(pTypes.map(_.map(_()).toList), tParams.toList, rt, fun)
            case b: ScBindingPattern => ScBindingPattern.getCompoundCopy(rt, b)
            case f: ScFieldId => ScFieldId.getCompoundCopy(rt, f)
            case named => named
          }, s.hasRepeatedParam
        ), rt)
    }, typesMap.map {
      case (s, sign) => (s, sign.updateTypes(_.recursiveUpdateImpl(updates, visited, isLazySubtype = true)))
    })
  }

  override def recursiveVarianceUpdateModifiable[T](data: T, update: (ScType, Variance, T) => (Boolean, ScType, T),
                                                    v: Variance = Covariant, revertVariances: Boolean = false): ScType = {
    update(this, v, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        val updSignatureMap = signatureMap.map {
          case (s: Signature, tp) =>
            val tParams = s.typeParams.update(_.recursiveVarianceUpdateModifiable(newData, update, Covariant))
            val paramTypes = s.substitutedTypes.map(_.map(f => () => f().recursiveVarianceUpdateModifiable(newData, update, Covariant)))
            val updSignature = new Signature(s.name, paramTypes, tParams, ScSubstitutor.empty, s.namedElement, s.hasRepeatedParam)
            (updSignature, tp.recursiveVarianceUpdateModifiable(newData, update, Covariant))
        }
        new ScCompoundType(components.map(_.recursiveVarianceUpdateModifiable(newData, update, v)), updSignatureMap, typesMap.map {
          case (s, sign) => (s, sign.updateTypes(_.recursiveVarianceUpdateModifiable(newData, update, Covariant)))
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
          val t = w1.equiv(w2, undefinedSubst, falseUndef)
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
              val f = t.equiv(t1, undefinedSubst, falseUndef)
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
                var t = bounds1.lowerBound.equiv(bounds2.lowerBound, undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
                t = bounds1.upperBound.equiv(bounds2.upperBound, undefinedSubst, falseUndef)
                if (!t._1) return (false, undefinedSubst)
                undefinedSubst = t._2
            }
          }
          (true, undefinedSubst)
        }
      case _ =>
        val needOnlyComponents = signatureMap.isEmpty && typesMap.isEmpty || ScCompoundType(components).conforms(this)
        if (needOnlyComponents) {
          val filtered = components.filter {
            case t if t.isAny => false
            case t if t.isAnyRef =>
              if (!r.conforms(AnyRef)) return (false, undefinedSubst)
              false
            case ScDesignatorType(obj: PsiClass) if obj.qualifiedName == "java.lang.Object" =>
              if (!r.conforms(AnyRef)) return (false, undefinedSubst)
              false
            case _ => true
          }
          if (filtered.length == 1) filtered.head.equiv(r, undefinedSubst, falseUndef)
          else (false, undefinedSubst)
        } else (false, undefinedSubst)
    }
  }
}

object ScCompoundType {
  def fromPsi(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder], typeDecls: Seq[ScTypeAlias])
             (implicit projectContext: ProjectContext): ScCompoundType = {
    val signatureMapVal: mutable.HashMap[Signature, ScType] = new mutable.HashMap[Signature, ScType] {
      override def elemHashCode(s : Signature): Int = s.name.hashCode * 31 + {
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
            e => (Signature(e), e.`type`().getOrAny)
          }
          signatureMapVal ++= varDecl.declaredElements.map {
            e => (Signature(e), e.`type`().getOrAny)
          }
        case valDecl: ScValue =>
          signatureMapVal ++= valDecl.declaredElements.map {
            e => (Signature(e), e.`type`().getOrAny)
          }
      }
    }

    ScCompoundType(
      components,
      signatureMapVal.toMap,
      typeDecls.map {
        typeDecl => (typeDecl.name, TypeAliasSignature(typeDecl))
      }.toMap)
  }
}
