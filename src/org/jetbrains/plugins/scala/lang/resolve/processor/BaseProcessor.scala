package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.scope._
import com.intellij.psi._
import _root_.scala.collection.Set
import impl.light.LightMethod
import org.jetbrains.plugins.scala.lang.psi.api._
import expr.ScReferenceExpression
import statements.ScTypeAlias
import psi.types._
import psi.ScalaPsiElement
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import result.TypingContext
import toplevel.imports.usages.ImportUsed
import ResolveTargets._
import _root_.scala.collection.mutable.HashSet
import toplevel.typedef.ScTemplateDefinition
import psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)

  val boundClassKey: Key[PsiClass] = Key.create("bound.class.key")

  val FROM_TYPE_KEY: Key[ScType] = Key.create("from.type.key")
}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value]) extends PsiScopeProcessor {
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

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {}

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  def processType(t: ScType, place: ScalaPsiElement): Boolean = processType(t, place, ResolveState.initial)

  def processType(t: ScType, place: ScalaPsiElement, state: ResolveState): Boolean = {
    processType(t, place, state, false)
  }

  def processType(t: ScType, place: ScalaPsiElement, state: ResolveState, noBounds: Boolean): Boolean = {
    ProgressManager.checkCanceled

    def isInApplyCall = place.getContext match {
      case expr: ScReferenceExpression => expr.nameId.getText == "apply"
      case _ => false
    }
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
        if (e.isEnum) {
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
      case ScDesignatorType(e) => processElement(e, ScSubstitutor.empty, place, state)
      case ScTypeParameterType(_, Nil, _, upper, _) => processType(upper.v, place)
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
        val s: ScSubstitutor = new ScSubstitutor(Map.empty, Map.empty, Some(des)) followed proj.actualSubst
        processElement(proj.actualElement, s, place, state)
      }

      case StdType(name, tSuper) => (SyntheticClasses.get(place.getProject).byName(name): @unchecked) match {
        case Some(c) => {
          if (!c.processDeclarations(this, state, null, place) ||
                  !(tSuper match {
                    case Some(ts) => processType(ts, place)
                    case _ => true
                  })) return false

          if (name == "Any") {
            for (m <- c.syntheticMethods(place.getResolveScope)) {
              m.name match {
                case "toString" | "hashCode" | "equals" => this.execute(m, state)
                case _ => //do nothing
              }
            }
          }
          true
        }
        case None => true//nothing to do
      }

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

        if (kinds.contains(CLASS)) {
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

  private def processElement (e : PsiNamedElement, s : ScSubstitutor, place: ScalaPsiElement, state: ResolveState) = {
    e match {
      case ta: ScTypeAlias => processType(s.subst(ta.upperBound.getOrElse(Any)), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))

      //need to process scala way
      case clazz: PsiClass =>
        TypeDefinitionMembers.processDeclarations(clazz, this, state.put(ScSubstitutor.key, s),
          null, place)

      case des => des.processDeclarations(this, state.put(ScSubstitutor.key, s), null, place)
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
