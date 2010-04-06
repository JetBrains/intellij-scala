package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import processor.ResolveProcessor
import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import psi.types._

import result.{TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import collection.{Seq, Set}
import psi.implicits.{ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern}
import psi.api.toplevel.typedef.{ScClass, ScObject}
import psi.api.toplevel.imports.usages.{ImportUsed}
class ExtractorResolveProcessor(ref: ScReferenceElement,
                                refName: String,
                                kinds: Set[ResolveTargets.Value],
                                expected: Option[ScType]/*, patternsCount: Int, lastSeq: Boolean*/)
        extends ResolveProcessor(kinds, ref, refName) {
  
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case o: ScObject if o.isPackageObject => return true
        case clazz: ScClass if clazz.isCase => {
          candidatesSet.clear
          candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
          return false //find error  about existing unapply in companion during annotation under case class
        }
        case ta: ScTypeAliasDefinition => {
          val alType = ta.aliasedType(TypingContext.empty)
          for (tp <- alType) {
            ScType.extractClassType(tp) match {
              case Some((clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase => {
                candidatesSet.clear
                candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
                return false
              }
              case _ =>
            }
          }
        }
        case obj: ScObject => {
          for (sign <- obj.signaturesByName("unapply")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state))
          }
          //unapply has bigger priority then unapplySeq
          if (candidatesSet.isEmpty)
          for (sign <- obj.signaturesByName("unapplySeq")) {
            val m = sign.method
            val subst = sign.substitutor
            candidatesSet += new ScalaResolveResult(m, getSubst(state).followed(subst), getImports(state))
          }
          return true
        }
        case bind: ScBindingPattern => {
          candidatesSet += new ScalaResolveResult(bind, getSubst(state), getImports(state))
        }
        case _ => return true
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    //todo: Local type inference
    expected match {
      case Some(tp) => {
          for (applicable <- candidatesSet) {
            applicable.element match {
              case fun: ScFunction => {
                val clauses = fun.paramClauses.clauses
                if (clauses.length != 0) {
                  if (clauses.apply(0).parameters.length == 1) {
                    for (paramType <- clauses(0).parameters.apply(0).getType(TypingContext.empty)) {
                      if (tp equiv applicable.substitutor.subst(paramType)) return Array(applicable)
                    }
                  }
                }
              }
              case _ =>
            }
          }
      }
      case _ =>
    }
    return candidatesSet.toArray
  }
}
