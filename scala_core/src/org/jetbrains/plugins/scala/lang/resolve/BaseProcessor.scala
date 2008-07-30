package org.jetbrains.plugins.scala.lang.resolve

import psi.api.toplevel.ScPolymorphicElement
import psi.api.expr.{ScSuperReference, ScThisReference}
import psi.api.base.{ScStableCodeReferenceElement, ScFieldId}
import psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages
import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api._
import statements.{ScVariable, ScTypeAlias}
import statements.params.{ScTypeParam, ScParameter}
import base.patterns.ScBindingPattern
import psi.types._
import psi.ScalaPsiElement
import psi.api.toplevel.packaging.ScPackaging
import psi.api.statements.ScFun

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets]) extends PsiScopeProcessor {

  protected val candidatesSet: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def candidates[T >: ScalaResolveResult]: Array[T] = candidatesSet.toArray[T]

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    def shouldProcess(c: Class[_]): Boolean = {
      if (kinds == null) true
      else if (classOf[PsiPackage].isAssignableFrom(c)) kinds contains ResolveTargets.PACKAGE
      else if (classOf[PsiClass].isAssignableFrom(c)) (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT) ||
              (kinds contains ResolveTargets.METHOD) //case classes get 'apply' generated
      else if (classOf[PsiVariable].isAssignableFrom(c)) (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
      else if (classOf[PsiMethod].isAssignableFrom(c)) kinds contains (ResolveTargets.METHOD)
      else false
    }
  }

  def getHint[T](hintClass: Class[T]): T = {
    if (hintClass == classOf[ElementClassHint]) {
      return MyElementClassHint.asInstanceOf[T]
    }
    return null.asInstanceOf[T]
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {
  }

  import ResolveTargets._

  protected def kindMatches(element: PsiElement): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage => kinds contains PACKAGE
            case _: ScPackaging => kinds contains PACKAGE
            case _: ScObject => kinds contains OBJECT
            case _: ScTypeParam => kinds contains CLASS
            case _: ScTypeAlias => kinds contains CLASS
            case clazz: ScClass => (kinds contains CLASS) || (kinds.contains(CLASS) && clazz.isCase)
            case _: ScTypeDefinition => kinds contains CLASS
            case c: PsiClass => {
              if (kinds contains CLASS) true
              else {
                def isStaticCorrect(clazz: PsiClass): Boolean = {
                  val cclazz = clazz.getContainingClass
                  cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
                }
                isStaticCorrect(c)
              }
            }
            case patt: ScBindingPattern => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case patt: ScFieldId => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case _: ScParameter => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case _: PsiField => kinds contains VAR
            case _ => false
          })

  import psi.impl.toplevel.synthetic.SyntheticClasses
  def processType(t: ScType, place: ScalaPsiElement): Boolean = t match {
    case ScDesignatorType(e) if !e.isInstanceOf[ScPolymorphicElement] => //scala ticket 425
      e.processDeclarations(this, ResolveState.initial, null, place)
    case ScPolymorphicType(poly, subst) => processType(subst.subst(poly.upperBound), place)

    case p: ScParameterizedType => p.designated match {
      case ta: ScTypeAlias => processType(p.substitutor.subst(ta.upperBound), place)
      case des => des.processDeclarations(this, ResolveState.initial.put(ScSubstitutor.key, p.substitutor), null, place)
    }
    case proj : ScProjectionType => ScType.extractClassType(proj) match {
      case Some((c, s)) => c.processDeclarations(this, ResolveState.initial.put(ScSubstitutor.key, s), null, place)
      case None => true
    }

    case ValType(name, _) => SyntheticClasses.get(place.getProject).byName(name) match {
      case Some(c) => c.processDeclarations(this, ResolveState.initial, null, place)
    }

    case ScCompoundType(comp, decls, types) => {
      if (kinds.contains(VAR) || kinds.contains(VAL) || kinds.contains(METHOD)) {
        for (decl <- decls) {
          for (declared <- decl.declaredElements) {
            if (!execute(declared, ResolveState.initial)) return false
          }
        }
      }

      if (kinds.contains(CLASS)) {
        for (t <- types) {
          if (!execute(t, ResolveState.initial)) return false
        }
      }

      for (c <- comp) {
        if (!processType(c, place)) return false
      }
      true
    }
    case ScSingletonType(path) => path match {
      case ref: ScStableCodeReferenceElement => ref.bind match {
        case Some(r) => r.element.processDeclarations(this, ResolveState.initial, null, place)
        case _ => true
      }
      case thisPath : ScThisReference => thisPath.refClass match {
        case Some(c) => c.processDeclarations(this, ResolveState.initial, null, place)
      }
      case superPath : ScSuperReference => superPath.refClass match {
        case Some(c) => c.processDeclarations(this, ResolveState.initial, null, place)
      }
    }
    case _ => true //todo
  }
}