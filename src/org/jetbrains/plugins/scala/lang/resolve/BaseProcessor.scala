package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.scope._
import com.intellij.psi._
import _root_.scala.collection.Set
import org.jetbrains.plugins.scala.lang.psi.api._
import statements.{ScTypeAlias}
import psi.types._
import psi.ScalaPsiElement
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import toplevel.imports.usages.ImportUsed

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {

  import _root_.scala.collection.mutable.HashSet

  protected val candidatesSet: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def changedLevel = true

  def candidates[T >: ScalaResolveResult]: Array[T] = candidatesSet.toArray[T]

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
    ProgressManager.getInstance.checkCanceled
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

      case StdType(name, tSuper) => SyntheticClasses.get(place.getProject).byName(name) match {
        case Some(c) => {
          c.processDeclarations(this, state, null, place) &&
                  (tSuper match {
                    case Some(ts) => processType(ts, place)
                    case _ => true
                  })
        }
      }

      case ScFunctionType(rt, params) if params.length == 0 => processType(rt, place)

      case ScCompoundType(components, declarations, types) => {
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

        for (c <- components) {
          if (!processType(c, place)) return false
        }
        true
      }
      case singl: ScSingletonType => processType(singl.pathType, place)
      case ex: ScExistentialType => processType(ex.skolem, place)
      case _ => true
    }
  }

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: ScalaPsiElement, state: ResolveState) = e match {
    case ta: ScTypeAlias => processType(s.subst(ta.upperBound), place)

    //need to process scala way
    case clazz : PsiClass =>
      TypeDefinitionMembers.processDeclarations(clazz, this, state.put(ScSubstitutor.key, s),
        null, place)

    case des => des.processDeclarations(this, state.put(ScSubstitutor.key, s), null, place)
  }

  protected def getSubst(state: ResolveState) = {
    val subst = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  protected def getImports(state: ResolveState): Set[ImportUsed] = {
    val used = state.get(ImportUsed.key)
    if (used == null) Set[ImportUsed]() else used
  }
 
}
