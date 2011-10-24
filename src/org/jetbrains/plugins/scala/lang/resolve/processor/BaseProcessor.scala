package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.scope._
import com.intellij.psi._
import _root_.scala.collection.Set
import impl.compiled.ClsClassImpl
import impl.light.LightMethod
import org.jetbrains.plugins.scala.lang.psi.api._
import base.types.ScTypeProjection
import statements.ScTypeAlias
import psi.types._
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import result.{Success, TypingContext}
import toplevel.imports.usages.ImportUsed
import ResolveTargets._
import _root_.scala.collection.mutable.HashSet
import psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import toplevel.ScTypedDefinition
import toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.extensions._
import psi.impl.ScalaPsiManager

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)

  val boundClassKey: Key[PsiClass] = Key.create("bound.class.key")

  val FROM_TYPE_KEY: Key[ScType] = Key.create("from.type.key")
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {
  protected val candidatesSet: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def changedLevel = true

  def rrcandidates: Array[ResolveResult] = {
    val set = candidatesS
    val size = set.size
    val res = JavaArrayFactoryUtil.ResolveResultFactory.create(size)
    if (size == 0) return res
    val iter = set.iterator
    var count = 0
    while (iter.hasNext) {
      val next = iter.next()
      res(count) = next
      count += 1
    }
    res
  }

  def candidates: Array[ScalaResolveResult] = {
    val set = candidatesS
    val size = set.size
    val res = JavaArrayFactoryUtil.ScalaResolveResultFactory.create(size)
    if (size == 0) return res
    val iter = set.iterator
    var count = 0
    while (iter.hasNext) {
      val next = iter.next()
      res(count) = next
      count += 1
    }
    res
  }

  def candidatesS: Set[ScalaResolveResult] = candidatesSet


  //todo: fix this ugly performance improvement
  private var classKind = true
  def setClassKind(b: Boolean) {
    classKind = b
  }
  def getClassKind = {
    classKind && ((kinds contains ResolveTargets.CLASS) ||
      (kinds contains ResolveTargets.OBJECT) ||
      (kinds contains ResolveTargets.METHOD))
  }

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    import ElementClassHint.DeclarationKind
    def shouldProcess(kind: DeclarationKind): Boolean = {
      kind match {
        case null => true
        case DeclarationKind.PACKAGE => kinds contains ResolveTargets.PACKAGE
        case DeclarationKind.CLASS if classKind => (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT) ||
                (kinds contains ResolveTargets.METHOD) //case classes get 'apply' generated
        case DeclarationKind.VARIABLE => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclarationKind.FIELD => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclarationKind.METHOD => kinds contains (ResolveTargets.METHOD)
        case _ => false
      }
    }
  }

  def getHint[T](hintKey: Key[T]): T = {
    hintKey match {
      case ElementClassHint.KEY => MyElementClassHint.asInstanceOf[T]
      case _ => null.asInstanceOf[T]
    }
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) {}

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  def processType(t: ScType, place: PsiElement): Boolean = processType(t, place, ResolveState.initial)

  def processType(t: ScType, place: PsiElement, state: ResolveState): Boolean =
    processType(t, place, state, false)



  def processType(t: ScType, place: PsiElement, state: ResolveState, noBounds: Boolean): Boolean =
    processType(t, place, state, noBounds, true)

  def processType(t: ScType, place: PsiElement, state: ResolveState,
                  noBounds: Boolean, updateWithProjectionSubst: Boolean): Boolean = {
    ProgressManager.checkCanceled()

    t match {
      case ScDesignatorType(clazz: PsiClass) if clazz.getQualifiedName == "java.lang.String" => {
        val plusMethod: ScType => ScSyntheticFunction = SyntheticClasses.get(place.getProject).stringPlusMethod
        if (plusMethod != null) execute(plusMethod(t), state) //add + method
      }
      case _ =>
    }

    t match {
      case ScThisType(clazz) => {
        val clazzType: ScType = clazz.getTypeWithProjections(TypingContext.empty).getOrElse(return true)
        processType(if (noBounds) clazzType else clazz.selfType match {
          case Some(selfType) => Bounds.glb(clazzType, selfType)
          case _ => clazzType
        }, place, state)
      }
      case d@ScDesignatorType(e: PsiClass) if d.isStatic && !e.isInstanceOf[ScTemplateDefinition] => {
        //not scala from scala
        var break = true
        for (method <- e.getMethods if break && method.hasModifierProperty("static")) {
          if (!execute(method, state)) break = false
        }
        for (cl <- e.getInnerClasses if break && cl.hasModifierProperty("static")) {
          if (!execute(cl, state)) break = false
        }
        for (field <- e.getFields if break && field.hasModifierProperty("static")) {
          if (!execute(field, state)) break = false
        }

        //todo: duplicate TypeDefinitionMembers
        //fake enum static methods
        val isJavaSourceEnum = !e.isInstanceOf[ClsClassImpl] && e.isEnum
        if (isJavaSourceEnum) {
          val elementFactory: PsiElementFactory = JavaPsiFacade.getInstance(e.getProject).getElementFactory
          //todo: cache like in PsiClassImpl
          val valuesMethod: PsiMethod = elementFactory.createMethodFromText("public static " + e.getName +
                  "[] values() {}", e)
          val valueOfMethod: PsiMethod = elementFactory.createMethodFromText("public static " + e.getName +
                  " valueOf(String name) throws IllegalArgumentException {}", e)
          val values = new LightMethod(e.getManager, valuesMethod, e)
          val valueOf = new LightMethod(e.getManager, valueOfMethod, e)
          if (!execute(values, state)) return false
          if (!execute(valueOf, state)) return false
        }
        break
      }
      case ScDesignatorType(e: ScTypedDefinition) if place.isInstanceOf[ScTypeProjection] =>
        e.getType(TypingContext.empty) match {
          case Success(tp, _) => processType(tp, place, state, noBounds)
          case _ => true
        }
      case ScDesignatorType(e) => processElement(e, ScSubstitutor.empty, place, state)
      case ScTypeParameterType(_, Nil, _, upper, _) => processType(upper.v, place, state)
      case j: JavaArrayType =>
        processType(j.getParameterizedType(place.getProject, place.getResolveScope).
                getOrElse(return true), place, state)
      case p@ScParameterizedType(des, typeArgs) => {
        p.designator match {
          case ScTypeParameterType(_, _, _, upper, _) => processType(p.substitutor.subst(upper.v), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
          case _ => p.designated match {
            case Some(des) => processElement(des, p.substitutor, place, state)
            case None => true
          }
        }
      }
      case proj@ScProjectionType(projectd, _, _) if proj.actualElement.isInstanceOf[ScTypeAlias] => {
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        val upper = ta.upperBound.getOrElse(return true)
        processType(subst.subst(upper), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
      }
      case proj@ScProjectionType(des, elem, subst) => {
        val s: ScSubstitutor = if (updateWithProjectionSubst)
          new ScSubstitutor(Map.empty, Map.empty, Some(proj)) followed proj.actualSubst
        else proj.actualSubst
        processElement(proj.actualElement, s, place, state)
      }

      case StdType(name, tSuper) =>
        SyntheticClasses.get(place.getProject).byName(name) match {
          case Some(c) => {
            if (!c.processDeclarations(this, state, null, place) ||
                    !(tSuper match {
                      case Some(ts) => processType(ts, place)
                      case _ => true
                    })) return false
          }
          case None => //nothing to do
        }

        val scope = place.getResolveScope
        val obj: PsiClass = ScalaPsiManager.instance(place.getProject).getCachedClass(scope, "java.lang.Object")
        if (obj != null) {
          val namesSet = Set("hashCode", "toString", "equals")
          val methods = obj.getMethods.iterator
          while (methods.hasNext) {
            val method = methods.next()
            if (name == "AnyRef" || namesSet.contains(method.getName)) {
              if (!execute(method, state)) return false
            }
          }
        }
        true

      case ft@ScFunctionType(rt, params) => {
        ft.resolveFunctionTrait(place.getProject).map(processType((_: ScType), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))).getOrElse(true)
      }

      case tp@ScTupleType(comps) => {
        tp.resolveTupleTrait(place.getProject).map(processType((_: ScType), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))).getOrElse(true)
      }

      case comp@ScCompoundType(components, declarations, types, substitutor) => {
        val oldSubst = state.get(ScSubstitutor.key).getOrElse(ScSubstitutor.empty)
        val newState = state.put(ScSubstitutor.key, substitutor.followed(oldSubst))
        if (kinds.contains(VAR) || kinds.contains(VAL) || kinds.contains(METHOD)) {
          for (declaration <- declarations) {
            for (declared <- declaration.declaredElements) {
              if (!execute(declared, newState)) return false
            }
          }
        }

        if (kinds.contains(CLASS))
        {
          for (t <- types) {
            if (!execute(t, newState)) return false
          }
        }

        //todo: comps already substituted
        if (!TypeDefinitionMembers.processDeclarations(comp, this, newState, null, place)) return false
        true
      }
      case ex: ScExistentialType => processType(ex.skolem, place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
      case z: ScExistentialArgument => processType(z.upperBound, place, state.put(ScSubstitutor.key, ScSubstitutor.empty)); processType(z.lowerBound, place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
      case _ => true
    }
  }

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: PsiElement, state: ResolveState) = {
    val subst = state.get(ScSubstitutor.key)
    val newSubst = if (subst != null) subst followed s else s
    e match {
      case ta: ScTypeAlias =>
        processType(s.subst(ta.upperBound.getOrAny), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
      //need to process scala way
      case clazz: PsiClass =>
        TypeDefinitionMembers.processDeclarations(clazz, this, state.put(ScSubstitutor.key, newSubst), null, place)
      case des: ScTypedDefinition =>
        des.getType(TypingContext.empty) match {
          case Success(tp, _) =>
            processType(newSubst subst tp, place, state.put(ScSubstitutor.key, ScSubstitutor.empty), false, false)
          case _ => true
        }
      case pack: ScPackage =>
        pack.processDeclarations(this, state.put(ScSubstitutor.key, newSubst), null, place, true)
      case des =>
        des.processDeclarations(this, state.put(ScSubstitutor.key, newSubst), null, place)
    }
  }

  protected def getSubst(state: ResolveState) = {
    val subst: ScSubstitutor = state.get(ScSubstitutor.key)
    if (subst == null) ScSubstitutor.empty else subst
  }

  protected def getImports(state: ResolveState): Set[ImportUsed] = {
    val used = state.get(ImportUsed.key)
    if (used == null) Set[ImportUsed]() else used
  }

  protected def getBoundClass(state: ResolveState): PsiClass = {
    state.get(BaseProcessor.boundClassKey)
  }

  protected def getFromType(state: ResolveState): Option[ScType] = {
    state.get(BaseProcessor.FROM_TYPE_KEY).toOption
  }
}
