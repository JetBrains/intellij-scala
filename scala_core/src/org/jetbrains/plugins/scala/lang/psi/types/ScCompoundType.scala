package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import _root_.scala.collection.mutable.{HashSet, HashMap}
import api.statements._
import com.intellij.psi.PsiTypeParameter

case class ScCompoundType(val components: Seq[ScType], val decls: Seq[ScDeclaredElementsHolder], val typeDecls: Seq[ScTypeAlias]) extends ScType{
  //compound types are checked by checking the set of signatures in their refinements
  val signatures = new HashSet[FullSignature]

  type Bounds = Pair[ScType, ScType]
  val types = new HashMap[String, Bounds]

  for (typeDecl <- typeDecls) {
    types += ((typeDecl.name, (typeDecl.lowerBound, typeDecl.upperBound)))
  }

  for (decl <- decls) {
    decl match {
      case fun: ScFunction =>
        signatures += new FullSignature(new PhysicalSignature(fun, ScSubstitutor.empty), fun.returnType)
      case varDecl: ScVariable => {
        varDecl.typeElement match {
          case Some(te) => for (e <- varDecl.declaredElements) {
            signatures += new FullSignature(new Signature(e.name, Seq.empty, Array(), ScSubstitutor.empty), te.getType)
            signatures += new FullSignature(new Signature(e.name + "_", Seq.singleton(te.getType), Array(), ScSubstitutor.empty), Unit) //setter
          }
          case None =>
        }
      }
      case valDecl: ScValue => valDecl.typeElement match {
        case Some(te) => for (e <- valDecl.declaredElements) {
          signatures += new FullSignature(new Signature(e.name, Seq.empty, Array(), ScSubstitutor.empty), te.getType)
        }
        case None =>
      }
    }
  }

  override def equiv(t: ScType) = t match {
    case other : ScCompoundType => {
      components.equalsWith (other.components) (_ equiv _) &&
      signatures.size == other.signatures.size &&
      signatures.elements.forall {sig => other.signatures.find(sig equals _) match {
        case None => false
        case Some(sig1) => sig.retType equiv sig1.retType
      }
      } &&
      typesMatch(types, other.types)
    }
    case _ => false
  }

  private def typesMatch(types1 : HashMap[String, Bounds],
                         types2 : HashMap[String, Bounds]) : Boolean = {
    if (types1 != types.size) return false
    else {
      for ((name, bounds1) <- types1) {
        types2.get(name) match {
          case None => return false
          case Some (bounds2) => if (!(bounds1._1 equiv bounds2._1) ||
                                     !(bounds1._2 equiv bounds2._2)) return false
        }
      }
      true
    }
  }
}