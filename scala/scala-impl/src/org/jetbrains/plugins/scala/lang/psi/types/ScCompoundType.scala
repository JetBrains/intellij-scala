package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util.Objects

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, TypeParametersArrayExt, TypeVisitor, ValueType, _}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

final case class ScCompoundType private (
  components:   Seq[ScType],
  signatureMap: Map[TermSignature, ScType]          = Map.empty,
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
      case (sign: TermSignature, tp: ScType) =>
        tp.typeDepth
          .max(sign.typeParams.depth)
    } ++ typesMap.map {
      case (_: String, signature: TypeAliasSignature) =>
        signature.lowerBound.typeDepth
          .max(signature.upperBound.typeDepth)
          .max(signature.typeParams.toArray.depth)
    }
    val ints = components.map(_.typeDepth)
    val componentsDepth = if (ints.isEmpty) 0 else ints.max
    if (depths.nonEmpty) componentsDepth.max(depths.max + 1)
    else componentsDepth
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
    signatureMap: Map[TermSignature, ScType]          = Map.empty,
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
    val signatureMapVal = mutable.HashMap.empty[TermSignature, ScType]

    for (decl <- decls) {
      decl match {
        case fun: ScFunction => signatureMapVal += ((TermSignature(fun), fun.returnType.getOrAny))
        case varDecl: ScVariable =>
          signatureMapVal ++= varDecl.declaredElements.map {
            e => (TermSignature(e), e.`type`().getOrAny)
          }
          signatureMapVal ++= varDecl.declaredElements.map {
            e => (TermSignature(e), e.`type`().getOrAny)
          }
        case valDecl: ScValue =>
          signatureMapVal ++= valDecl.declaredElements.map {
            e => (TermSignature(e), e.`type`().getOrAny)
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
