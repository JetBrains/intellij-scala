package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import _root_.scala.collection.mutable.HashSet
import api.statements._
import com.intellij.psi.PsiTypeParameter

case class ScCompoundType(val components: Seq[ScType], decls: Seq[ScDeclaration], typeDecls: Seq[ScTypeAlias]) extends ScType{
  //compound types are checked by checking the set of signatures in their refinements
  val signatureSet = new HashSet[Signature] {
    override def elemHashCode(s : Signature) = s.name.hashCode* 31 + s.types.length
  }

  for (decl <- decls) {
    decl match {
      case fun: ScFunctionDeclaration => signatureSet += new PhysicalSignature(fun, ScSubstitutor.empty)
      case varDecl: ScVariableDeclaration => {
        val typeElement = varDecl.typeElement
        for (name <- varDecl.names) {
          signatureSet += new Signature(name, Seq.empty, Array(), ScSubstitutor.empty)
          typeElement match {
            case Some(te) => {
              signatureSet += new Signature(name + "_", Seq.single(te.getType), Array(), ScSubstitutor.empty) //setter
            }
            case None =>
          }
        }
      }
      case valDecl: ScValueDeclaration => for (name <- valDecl.names) {
        signatureSet += new Signature(name, Seq.empty, Array(), ScSubstitutor.empty)
      }
    }
  }
  //todo extract type alias information

  override def equiv(t: ScType) = t match {
    case other : ScCompoundType => {
      components.equalsWith (other.components) {(t1, t2) => t1 equiv t2} &&
      signatureSet.equals(other.signatureSet)
    }
    case _ => false
  }
}