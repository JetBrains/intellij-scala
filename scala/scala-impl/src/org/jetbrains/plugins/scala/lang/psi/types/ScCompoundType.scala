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
import org.jetbrains.plugins.scala.lang.psi.light.scala.{ScLightBindingPattern, ScLightFieldId, ScLightFunction}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, TypeParametersArrayExt, TypeVisitor, ValueType, _}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, ScSubstitutor, Update}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType private (
  components:   Seq[ScType],
  signatureMap: Map[Signature, ScType]          = Map.empty,
  typesMap:     Map[String, TypeAliasSignature] = Map.empty
)(implicit override val projectContext: ProjectContext) extends ScalaType with ValueType {

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

  override def updateSubtypes(updates: Array[Update], index: Int, visited: Set[ScType]): ScCompoundType = {
    ScCompoundType(components.map(_.recursiveUpdateImpl(updates, index, visited)), signatureMap.map {
      case (s: Signature, tp) =>

        val pTypes: Seq[Seq[() => ScType]] =
          s.substitutedTypes.map(_.map(f => () => f().recursiveUpdateImpl(updates, index, visited, isLazySubtype = true)))

        val typeParameters = s.typeParams.update(_.recursiveUpdateImpl(updates, index, visited, isLazySubtype = true))
        implicit val returnType: ScType = tp.recursiveUpdateImpl(updates, index, visited)

        (new Signature(
          s.name, pTypes, typeParameters, ScSubstitutor.empty, s.namedElement match {
            case function: ScFunction => ScLightFunction(function, pTypes, typeParameters)
            case pattern: ScBindingPattern => ScLightBindingPattern(pattern)
            case fieldId: ScFieldId => ScLightFieldId(fieldId)
            case named => named
          }, s.hasRepeatedParam
        ), returnType)
    }, typesMap.map {
      case (s, sign) => (s, sign.updateTypes(_.recursiveUpdateImpl(updates, index, visited, isLazySubtype = true)))
    })
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType = {
    val updSignatureMap = signatureMap.map {
      case (s: Signature, tp) =>
        val tParams = s.typeParams.updateWithVariance(_.recursiveVarianceUpdate(update, _, isLazySubtype = true), Covariant)
        val paramTypes = s.substitutedTypes.map(_.map(f => () => f().recursiveVarianceUpdate(update, Covariant, isLazySubtype = true)))
        val updSignature = new Signature(s.name, paramTypes, tParams, ScSubstitutor.empty, s.namedElement, s.hasRepeatedParam)
        (updSignature, tp.recursiveVarianceUpdate(update, Covariant))
    }
    ScCompoundType(components.map(_.recursiveVarianceUpdate(update, variance)), updSignatureMap, typesMap.map {
      case (s, sign) => (s, sign.updateTypes(_.recursiveVarianceUpdate(update, Covariant, isLazySubtype = true)))
    })
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    var lastConstraints = constraints
    r match {
      case r: ScCompoundType =>
        if (r == this) return lastConstraints
        if (components.length != r.components.length) return ConstraintsResult.Left
        val list = components.zip(r.components)
        val iterator = list.iterator
        while (iterator.hasNext) {
          val (w1, w2) = iterator.next()
          val t = w1.equiv(w2, lastConstraints, falseUndef)
          if (t.isLeft) return ConstraintsResult.Left

          lastConstraints = t.constraints
        }

        if (signatureMap.size != r.signatureMap.size) return ConstraintsResult.Left

        val iterator2 = signatureMap.iterator
        while (iterator2.hasNext) {
          val (sig, t) = iterator2.next()
          r.signatureMap.get(sig) match {
            case None => return ConstraintsResult.Left
            case Some(t1) =>
              val f = t.equiv(t1, lastConstraints, falseUndef)

              if (f.isLeft) return ConstraintsResult.Left
              lastConstraints = f.constraints
          }
        }

        val types1 = typesMap
        val types2 = r.typesMap
        if (types1.size != types2.size) ConstraintsResult.Left
        else {
          val types1iterator = types1.iterator
          while (types1iterator.hasNext) {
            val (name, bounds1) = types1iterator.next()
            types2.get(name) match {
              case None => return ConstraintsResult.Left
              case Some (bounds2) =>
                var t = bounds1.lowerBound.equiv(bounds2.lowerBound, lastConstraints, falseUndef)

                if (t.isLeft) return ConstraintsResult.Left
                lastConstraints = t.constraints

                t = bounds1.upperBound.equiv(bounds2.upperBound, lastConstraints, falseUndef)
                if (t.isLeft) return ConstraintsResult.Left

                lastConstraints = t.constraints
            }
          }
          lastConstraints
        }
      case _ =>
        val needOnlyComponents = signatureMap.isEmpty && typesMap.isEmpty || ScCompoundType(components).conforms(this)
        if (needOnlyComponents) {
          val filtered = components.filter {
            case t if t.isAny => false
            case t if t.isAnyRef =>
              if (!r.conforms(AnyRef)) return ConstraintsResult.Left
              false
            case ScDesignatorType(obj: PsiClass) if obj.qualifiedName == "java.lang.Object" =>
              if (!r.conforms(AnyRef)) return ConstraintsResult.Left
              false
            case _ => true
          }
          if (filtered.length == 1) filtered.head.equiv(r, lastConstraints, falseUndef)
          else ConstraintsResult.Left
        } else ConstraintsResult.Left
    }
  }
}

object ScCompoundType {
  def apply(
    components:   Seq[ScType],
    signatureMap: Map[Signature, ScType]          = Map.empty,
    typesMap:     Map[String, TypeAliasSignature] = Map.empty
  )(implicit projectContext: ProjectContext): ScCompoundType = {
    val (comps, sigs, types) =
      components.foldLeft((Seq.empty[ScType], signatureMap, typesMap)) {
        case (acc, compound: ScCompoundType) =>
          (acc._1 ++ compound.components, acc._2 ++ compound.signatureMap, acc._3 ++ compound.typesMap)
        case (acc, otherTpe) => (acc._1 :+ otherTpe, acc._2, acc._3)
      }

    new ScCompoundType(comps.distinct, sigs, types)
  }


  def fromPsi(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder], typeDecls: Seq[ScTypeAlias])
             (implicit projectContext: ProjectContext): ScCompoundType = {
    val signatureMapVal: mutable.HashMap[Signature, ScType] = new mutable.HashMap[Signature, ScType] {
      override def elemHashCode(s : Signature): Int =
        s.name.hashCode * 31 + s.paramClauseSizes.hash
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
