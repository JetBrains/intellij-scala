package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.scope._
import com.intellij.psi._
import _root_.scala.collection.Set
import org.jetbrains.plugins.scala.lang.psi.api._
import expr.ScReferenceExpression
import statements.{ScTypeAlias}
import psi.types._
import psi.ScalaPsiElement
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import toplevel.imports.usages.ImportUsed
import psi.impl.toplevel.synthetic.ScSyntheticClass

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {
  import _root_.scala.collection.mutable.HashSet

  protected val candidatesSet: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def changedLevel = true

  def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = candidatesSet.toArray[T]

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    import ElementClassHint.DeclaractionKind
    def shouldProcess(kind: DeclaractionKind): Boolean = {
      kind match {
        case null => true
        case DeclaractionKind.PACKAGE => kinds contains ResolveTargets.PACKAGE
        case DeclaractionKind.CLASS => (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT) ||
                (kinds contains ResolveTargets.METHOD) //case classes get 'apply' generated
        case DeclaractionKind.VARIABLE => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclaractionKind.FIELD => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclaractionKind.METHOD => kinds contains (ResolveTargets.METHOD)
        case _ => false
      }
    }
  }

  def getHint[T](hintKey: Key[T]): T = {
    if (hintKey == ElementClassHint.KEY) {
      return MyElementClassHint.asInstanceOf[T]
    } else {
      return null.asInstanceOf[T]
    }
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {
  }

  import ResolveTargets._

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  import psi.impl.toplevel.synthetic.SyntheticClasses

  def processType(t: ScType, place: ScalaPsiElement): Boolean = processType(t, place, ResolveState.initial)

  def processType(t: ScType, place: ScalaPsiElement, state: ResolveState): Boolean = {
    ProgressManager.checkCanceled

    def isInApplyCall = place.getContext match {
      case expr: ScReferenceExpression => expr.nameId.getText == "apply"
      case _ => false
    }

    t match {
      case ScDesignatorType(e) => processElement(e, ScSubstitutor.empty, place, state)
      case ScPolymorphicType(_, Nil, _, upper) => processType(upper.v, place)

      case p@ScParameterizedType(des, typeArgs) => {
        p.designator match {
          case ScPolymorphicType(_, _, _, upper) => processType(p.substitutor.subst(upper.v), place)
          case _ => p.designated match {
            case Some(des) => processElement(des, p.substitutor, place, state)
            case None => true
          }
        }
      }
      case proj: ScProjectionType => proj.resolveResult match {
        case Some(res) => processElement(res.element, res.substitutor, place, state)
        case None => true
      }

      case StdType(name, tSuper) => (SyntheticClasses.get(place.getProject).byName(name): @unchecked) match {
        case Some(c) => {
          c.processDeclarations(this, state, null, place) &&
                  (tSuper match {
                    case Some(ts) => processType(ts, place)
                    case _ => true
                  })
        }
      }

      case ft@ScFunctionType(rt, params) if isInApplyCall => {
        ft.resolveFunctionTrait(place.getProject).map(processType((_: ScType), place)).getOrElse(true)
      }

      case ft@ScFunctionType(rt, params) if params.isEmpty => {
        processType(rt, place)
      }

      case tp@ScTupleType(comps) => {
        tp.resolveTupleTrait(place.getProject).map(processType((_: ScType), place)).getOrElse(true)
      }

      case comp@ScCompoundType(components, declarations, types) => {
        if (kinds.contains(VAR) || kinds.contains(VAL) || kinds.contains(METHOD)) {
          for (declaration <- declarations) {
            for (declared <- declaration.declaredElements) {
              if (!execute(declared, state)) return false
            }
          }
        }

        if (kinds.contains(CLASS)) {
          for (t <- types) {
            if (!execute(t, state)) return false
          }
        }

        if (!TypeDefinitionMembers.processDeclarations(comp, this, state, null, place)) return false
        true
      }
      case singl: ScSingletonType => {
        // See test ThisTypeCompound.
        val qual = place match {
          case ref: ScReferenceExpression => ref.qualifier
          case _ => None
        }
        processType(singl.pathTypeInContext(qual), place)
      }
      case ex: ScExistentialType => processType(ex.skolem, place)
      case z: ScExistentialArgument => processType(z.upperBound, place); processType(z.lowerBound, place)
      case _ => true
    }
  }

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: ScalaPsiElement, state: ResolveState) = e match {
    case ta: ScTypeAlias => processType(s.subst(ta.upperBound.getOrElse(Any)), place)

    //need to process scala way
    case clazz: PsiClass =>
      TypeDefinitionMembers.processDeclarations(clazz, this, state.put(ScSubstitutor.key, s),
        null, place)

    case des => des.processDeclarations(this, state.put(ScSubstitutor.key, s), null, place)
  }

  protected def getSubst(state: ResolveState) = {
    val subst: ScSubstitutor = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  protected def getImports(state: ResolveState): Set[ImportUsed] = {
    val used = state.get(ImportUsed.key)
    if (used == null) Set[ImportUsed]() else used
  }

}
