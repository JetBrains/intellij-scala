package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import _root_.scala.collection.Set
import collection.mutable.HashSet
import psi.api.base.patterns.{ScPattern, ScBindingPattern}
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.ScalaPsiUtil
import caches.CachesUtil
import psi.api.statements.ScFunction
import psi.types.{ScType, PhysicalSignature, Signature, ScSubstitutor}


class CompletionProcessor(override val kinds: Set[ResolveTargets.Value],
                          val collectImplicits: Boolean = false,
                          forName: Option[String] = None,
                          postProcess: ScalaResolveResult => Unit = r => {}) extends BaseProcessor(kinds) {
  
  private val signatures = new HashSet[Signature]
  private val names = new HashSet[(String, Boolean)]

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    forName match {
      case Some(name) if element.isInstanceOf[PsiNamedElement] && element.asInstanceOf[PsiNamedElement].getName != name => return true
      case _ =>
    }
    lazy val substitutor: ScSubstitutor = {
      state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case x => x
      }
    }

    lazy val isRenamed: Option[String] = {
      state.get(ResolverEnv.nameKey) match {
        case null => None
        case x: String => Some(x)
      }
    }

    lazy val implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION) match {
      case null => None
      case x => Some(x)
    }
    lazy val isNamedParameter: Boolean = state.get(CachesUtil.NAMED_PARAM_KEY) match {
      case null => false
      case v => v.booleanValue
    }

    val fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY) match {
      case null => None
      case v => Some(v)
    }

    element match {
      case fun: ScFunction if fun.isConstructor => return true//do not add constructor
      case td: ScTypeDefinition if !names.contains(Tuple(td.getName, false)) => {
        if (kindMatches(td)) {
          val result = new ScalaResolveResult(td, substitutor, nameShadow = isRenamed,
            implicitFunction = implFunction, fromType = fromType)
          candidatesSet += result
          postProcess(result)
        }
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td: ScTypeDefinition) if kindMatches(td)=>
            val result = new ScalaResolveResult(td, substitutor,
              nameShadow = isRenamed, implicitFunction = implFunction, fromType = fromType)
            candidatesSet += result
            postProcess(result)
          case _ =>
        }
      }
      case named: PsiNamedElement => {
        if (kindMatches(element)) {
          element match {
            case method: PsiMethod => {
              val sign = new PhysicalSignature(method, substitutor)
              if (!signatures.contains(sign)) {
                signatures += sign
                val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType)
                candidatesSet += result
                postProcess(result)
              }
            }
            case bindingPattern: ScBindingPattern => {
              val sign = new Signature(isRenamed.getOrElse(bindingPattern.getName), Stream.empty, 0,
                substitutor, Some(bindingPattern))
              if (!signatures.contains(sign)) {
                signatures += sign
                val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType)
                candidatesSet += result
                postProcess(result)
              }
            }
            case _ => {
              if (!names.contains((named.getName, isNamedParameter))) {
                val result = new ScalaResolveResult(named, substitutor, nameShadow = isRenamed,
                  implicitFunction = implFunction, isNamedParameter = isNamedParameter, fromType = fromType)
                candidatesSet += result
                postProcess(result)
                names += Tuple(isRenamed.getOrElse(named.getName), isNamedParameter)
              }
            }
          }
        }
      }
      case pat : ScPattern => for (b <- pat.bindings) execute(b, state)
      case _ => // Is it really a case?
    }
    true
  }
}
