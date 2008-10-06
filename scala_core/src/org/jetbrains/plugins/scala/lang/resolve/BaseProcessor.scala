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
import psi.impl.toplevel.typedef.TypeDefinitionMembers

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {

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

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  import psi.impl.toplevel.synthetic.SyntheticClasses
  def processType(t: ScType, place: ScalaPsiElement): Boolean = t match {
    case ScDesignatorType(e) => processElement(e, ScSubstitutor.empty, place)
    case ScTypeAliasType(_, Nil, _, upper) => processType(upper, place)
    case ScTypeVariable(_, Nil, _, upper) => processType(upper, place)

    case p: ScParameterizedType => {
      p.designator match {
        case ScTypeAliasType(_, _, _, upper) => processType(p.substitutor.subst(upper), place)
        case ScTypeVariable(_, _, _, upper) => processType(p.substitutor.subst(upper), place)
        case _ => p.designated match {
          case Some(des) => processElement(des, p.substitutor, place)
          case None => true
        }
      }
    }
    case proj : ScProjectionType => proj.resolveResult match {
      case Some(res) => processElement(res.element, res.substitutor, place)
      case None => true
    }

    case StdType(name, tSuper) => SyntheticClasses.get(place.getProject).byName(name) match {
      case Some(c) => {
        c.processDeclarations(this, ResolveState.initial, null, place) &&
        (tSuper match {
          case Some (ts) => processType(ts, place)
          case _ => true
        })
      }
    }

    case ScFunctionType(rt, params) if params.length == 0 => processType(rt, place)

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
    case singl : ScSingletonType => processType(singl.pathType, place)
    case ex : ScExistentialType => processType(ex.skolem, place)
    case _ => true
  }

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: ScalaPsiElement) = e match {
    case ta: ScTypeAlias => processType(s.subst(ta.upperBound), place)

    //need to process scala way
    case clazz : PsiClass if !clazz.isInstanceOf[ScTypeDefinition] =>
      TypeDefinitionMembers.processDeclarations(clazz, this, ResolveState.initial.put(ScSubstitutor.key, s),
        null, place)

    case des => des.processDeclarations(this, ResolveState.initial.put(ScSubstitutor.key, s), null, place)
  }
}