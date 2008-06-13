package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import _root_.scala.collection.mutable.HashSet
import api.statements._
import com.intellij.psi.PsiTypeParameter

/** 
* @author ilyas
*/

case class ScFunctionType(returnType: ScType, params: Seq[ScType]) extends ScType

case class ScTupleType(components: Seq[ScType]) extends ScType

case class ScCompoundType(val components: Seq[ScType], decls: Seq[ScDeclaration], typeDecls: Seq[ScTypeAlias]) {
  //compound types are checked by checking the set of signatures in their refinements
  val signatureSet = new HashSet[Signature]
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
}