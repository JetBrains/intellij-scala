package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements._
import collection.mutable.{ListBuffer, HashMap}
import result.{TypingContext, Failure}
import com.intellij.psi.PsiElement

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(components: Seq[ScType], decls: Seq[ScDeclaredElementsHolder],
                          typeDecls: Seq[ScTypeAlias], subst: ScSubstitutor) extends ValueType {
  type Bounds = Pair[ScType, ScType]

  //compound types are checked by checking the set of signatures in their refinements
  val signatureMapVal = new HashMap[Signature, ScType] {
    override def elemHashCode(s : Signature) = s.name.hashCode* 31 + s.paramLength
  }
  private val typesVal = new HashMap[String, Bounds]
  private val problemsVal: ListBuffer[Failure] = new ListBuffer

  def problems: ListBuffer[Failure] = {
    init
    problemsVal
  }

  def types = {
    init
    typesVal
  }

  def signatureMap = {
    init
    signatureMapVal
  }

  private var isInitialized = false
  private def init {
    synchronized {
      if (isInitialized) return
      isInitialized = true

      for (typeDecl <- typeDecls) {
        typesVal += ((typeDecl.name, (typeDecl.lowerBound.getOrElse(Nothing), typeDecl.upperBound.getOrElse(Any))))
      }


      for (decl <- decls) {
        decl match {
          case fun: ScFunction =>
            signatureMapVal += ((new PhysicalSignature(fun, subst), fun.getType(TypingContext.empty).getOrElse(Any)))
          case varDecl: ScVariable => {
            varDecl.typeElement match {
              case Some(te) => for (e <- varDecl.declaredElements) {
                val varType = te.getType(TypingContext.empty(varDecl.declaredElements))
                varType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
                signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst), varType.getOrElse(Any)))
                signatureMapVal += ((new Signature(e.name + "_", Stream(varType.getOrElse(Any)), 1, subst), Unit)) //setter
              }
              case None =>
            }
          }
          case valDecl: ScValue => valDecl.typeElement match {
            case Some(te) => for (e <- valDecl.declaredElements) {
              val valType = te.getType(TypingContext.empty(valDecl.declaredElements))
              valType match {case f@Failure(_, _) => problemsVal += f; case _ =>}
              signatureMapVal += ((new Signature(e.name, Stream.empty, 0, subst), valType.getOrElse(Any)))
            }
            case None =>
          }
        }
      }
    }
  }

  def typesMatch(types1 : HashMap[String, Bounds], subst1: ScSubstitutor,
                         types2 : HashMap[String, Bounds], subst2: ScSubstitutor) : Boolean = {
    if (types1.size != types.size) return false
    else {
      for ((name, bounds1) <- types1) {
        types2.get(name) match {
          case None => return false
          case Some (bounds2) => if (!(subst1.subst(bounds1._1) equiv subst2.subst(bounds2._1)) ||
                                     !(subst1.subst(bounds1._2) equiv subst2.subst(bounds2._2))) return false
        }
      }
      true
    }
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts), decls, typeDecls, subst)

  override def updateThisType(place: PsiElement) =
    ScCompoundType(components.map(_.updateThisType(place)), decls, typeDecls, subst)

  override def updateThisType(tp: ScType) =
    ScCompoundType(components.map(_.updateThisType(tp)), decls, typeDecls, subst)
}