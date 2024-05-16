package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ResolveUtils, ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor.RecursionState
import org.jetbrains.plugins.scala.project.ProjectContext

object BaseProcessor {
  def unapply(p: BaseProcessor): Some[Set[ResolveTargets.Value]] = Some(p.kinds)

  def isImplicitProcessor(processor: PsiScopeProcessor): Boolean =
    processor match {
      case b: BaseProcessor => b.isImplicitProcessor
      case _                => false
    }

  //todo ugly recursion breakers, maybe we need general for type? What about performance?
  private case class RecursionState(visitedProjections: Set[PsiNamedElement],
                                    visitedTypeParameter: Set[TypeParameterType]) {

    def add(projection: PsiNamedElement): RecursionState =
      copy(visitedProjections = visitedProjections + projection)

    def add(tpt: TypeParameterType): RecursionState =
      copy(visitedTypeParameter = visitedTypeParameter + tpt)
  }

  private object RecursionState {
    val empty: RecursionState = RecursionState(Set.empty, Set.empty)
  }

}

abstract class BaseProcessor(val kinds: Set[ResolveTargets.Value])
                            (implicit val projectContext: ProjectContext) extends PsiScopeProcessor {
  protected var candidatesSet: Set[ScalaResolveResult] = Set.empty

  def isImplicitProcessor: Boolean = false

  def changedLevel: Boolean = true

  protected var accessibility = true
  def doNotCheckAccessibility(): Unit = {accessibility = false}

  override final def execute(element: PsiElement, state: ResolveState): Boolean =
    element match {
      case namedElement: PsiNamedElement =>
        val kindMatches = ResolveUtils.kindMatches(namedElement, kinds)
        if (kindMatches) {
          val onlyProcessElementsWithStableType = kinds.contains(ResolveTargets.HAS_STABLE_TYPE)
          val stateNew = if (onlyProcessElementsWithStableType) state.withStableTypeExpected else state
          execute(namedElement)(stateNew)
        }
        else true
      case _ =>
        true
    }

  protected def execute(namedElement: PsiNamedElement)
                       (implicit state: ResolveState): Boolean

  final def candidates: Array[ScalaResolveResult] = {
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
  def setClassKind(classKind: Boolean): Unit = {
    this.classKind = classKind
  }
  def getClassKind: Boolean = {
    classKind && getClassKindInner
  }
  def getClassKindInner: Boolean = {
    (kinds contains ResolveTargets.CLASS) ||
      (kinds contains ResolveTargets.OBJECT) ||
      (kinds contains ResolveTargets.METHOD)
  }

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    import com.intellij.psi.scope.ElementClassHint.DeclarationKind
    override def shouldProcess(kind: DeclarationKind): Boolean = {
      kind match {
        case null => true
        case DeclarationKind.PACKAGE => kinds contains ResolveTargets.PACKAGE
        case DeclarationKind.CLASS if classKind =>
          (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT) ||
            (kinds contains ResolveTargets.METHOD) //case classes get 'apply' generated
        case DeclarationKind.VARIABLE => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclarationKind.FIELD => (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
        case DeclarationKind.METHOD => kinds contains ResolveTargets.METHOD
        case _ => false
      }
    }
  }

  override def getHint[T](hintKey: Key[T]): T = {
    hintKey match {
      case ElementClassHint.KEY => MyElementClassHint.asInstanceOf[T]
      case _ => null.asInstanceOf[T]
    }
  }

  def isAccessible(named: PsiNamedElement, place: PsiElement): Boolean = {
    val memb: PsiMember = {
      named match {
        case memb: PsiMember => memb
        case _ => named.nameContext match {
          case memb: PsiMember => memb
          case _ => return true //something strange
        }
      }
    }
    ResolveUtils.isAccessible(memb, place)
  }

  def processType(
    t:                        ScType,
    place:                    PsiElement,
    state:                    ResolveState = ScalaResolveState.empty,
    updateWithProjectionType: Boolean      = true
  ): Boolean = processTypeImpl(t, place, state, updateWithProjectionType)(RecursionState.empty)

  private def processTypeImpl(
    t:                         ScType,
    place:                     PsiElement,
    state:                     ResolveState = ScalaResolveState.empty,
    updateWithProjectionSubst: Boolean = true
  )(implicit
    recState: RecursionState
  ): Boolean = {
    ProgressManager.checkCanceled()

    t match {
      case ScDesignatorType(clazz: PsiClass) if clazz.qualifiedName == "java.lang.String" =>
        val plusMethod: ScType => ScSyntheticFunction = SyntheticClasses.get(place.getProject).stringPlusMethod
        if (plusMethod != null) execute(plusMethod(t), state) //add + method
      case _ =>
    }

    t match {
      case ScThisType(clazz) =>
        clazz.selfType match {
          case None =>
            processElement(clazz, ScSubstitutor.empty, place, state)
          case Some(ScThisType(`clazz`)) =>
            //to prevent SOE, let's process Element
            processElement(clazz, ScSubstitutor.empty, place, state)
          case Some(ScProjectionType(_, element)) if recState.visitedProjections.contains(element) =>
            //recursion detected
            true
          case Some(selfType) =>
            val clazzType = clazz.getTypeWithProjections().getOrElse(return true)
            if (selfType.conforms(clazzType)) {
              val newState = state
                .withCompoundOrSelfType(t)
                .withSubstitutor(ScSubstitutor(ScThisType(clazz)))
              processTypeImpl(selfType, place, newState)
            }
            else if (clazzType.conforms(selfType)) {
              processElement(clazz, ScSubstitutor.empty, place, state)
            }
            else {
              val glb = selfType.glb(clazzType)
              val newState = state.withCompoundOrSelfType(t)
              processTypeImpl(glb, place, newState)
            }
        }
      case d@ScDesignatorType(e: PsiClass) if d.isStatic && !e.isInstanceOf[ScTemplateDefinition] =>
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
        if (!break) return false
        processEnum(e, execute(_, state))
      case ScDesignatorType(o: ScObject) =>
        processElement(o, ScSubstitutor.empty, place, state)
      case ScDesignatorType(e: ScTypedDefinition) if place.isInstanceOf[ScTypeProjection] =>
        val result: TypeResult =
          e match {
            case p: ScParameter => p.getRealParameterType
            case _ => e.`type`()
          }
        result match {
          case Right(tp) => processTypeImpl(tp, place, state)
          case _ => true
        }
      case ScDesignatorType(e)    => processElement(e, ScSubstitutor.empty, place, state)
      case tpt: TypeParameterType => processTypeImpl(tpt.upperType, place, state, updateWithProjectionSubst = false)
      case j: JavaArrayType =>
        implicit val elementScope: ElementScope = place.elementScope
        processTypeImpl(j.getParameterizedType.getOrElse(return true), place, state)
      case p@ParameterizedType(designator, typeArgs) =>
        designator match {
          case tpt: TypeParameterType =>
            if (recState.visitedTypeParameter.contains(tpt)) return true
            val newState = state.withSubstitutor(ScSubstitutor(p))
            val upper    = tpt.upperType

            val substedType =
              if (upper.isAny || upper.isAnyRef) upper
              else                               p.substitutor(ParameterizedType(tpt.upperType, typeArgs))

            processTypeImpl(substedType, place, newState)(recState.add(tpt))
          case _ => p.extractDesignatedType(expandAliases = false) match {
            case Some((des, subst)) =>
              processElement(des, subst, place, state)
            case None => true
          }
        }
      case proj: ScProjectionType =>
        val withActual = new ScProjectionType.withActual(updateWithProjectionSubst)
        proj match {
          case withActual(elem, s) =>
            if (recState.visitedProjections.contains(elem))
              return true

            elem match {
              case alias: ScTypeAlias =>
                val upper = alias.upperBound.getOrElse(return true)
                processTypeImpl(s(upper), place, state.withSubstitutor(ScSubstitutor.empty))(recState.add(alias))
              case elem =>
                val subst =
                  if (updateWithProjectionSubst) ScSubstitutor(proj) followed s
                  else                           s
                processElement(elem, subst, place, state)(recState.add(elem))
            }
        }
      case lit: ScLiteralType => processType(lit.wideType, place, state, updateWithProjectionSubst)
      case StdType(name, tSuper) =>
        SyntheticClasses.get(place.getProject).byName(name) match {
          case Some(c) =>
            if (!c.processDeclarations(this, state, null, place) ||
              !(tSuper match {
                case Some(ts) => processTypeImpl(ts, place)
                case _ => true
              })) return false
          case None => //nothing to do
        }

        val scope = place.resolveScope
        val obj: PsiClass = ScalaPsiManager.instance(place.getProject).getCachedClass(scope, "java.lang.Object").orNull
        if (obj != null) {
          val namesSet = Set("hashCode", "toString", "equals", "getClass")
          val methods = obj.getMethods.iterator
          while (methods.hasNext) {
            val method = methods.next()
            if (name == "AnyRef" || namesSet.contains(method.name)) {
              if (!execute(method, state)) return false
            }
          }
        }
        true
      case comp: ScCompoundType => processDeclarations(comp, this, state, null, place)
      case and: ScAndType       => processDeclarations(and, this, state, null, place)
      case or: ScOrType         => processTypeImpl(or.join, place, state, updateWithProjectionSubst)
      case ex: ScExistentialType =>
        processTypeImpl(ex.quantified, place, state.withSubstitutor(ScSubstitutor.empty))
      case ScExistentialArgument(_, _, _, upper) =>
        processTypeImpl(upper, place, state)
      case _ => true
    }
  }

  private def processElement(e: PsiNamedElement, s: ScSubstitutor, place: PsiElement, state: ResolveState)
                            (implicit recState: RecursionState): Boolean = {
    val subst = state.substitutor
    val compoundOrThis = state.compoundOrThisType //todo: looks like ugly workaround
    val newSubst = if (compoundOrThis.nonEmpty) subst else subst.followed(s)

    e match {
      case ta: ScTypeAlias =>
        if (recState.visitedProjections.contains(ta)) return true
        val newState = state.withSubstitutor(ScSubstitutor.empty)
        processTypeImpl(s(ta.upperBound.getOrAny).inferValueType, place, newState)(recState.add(ta))
      //need to process scala way
      case clazz: PsiClass =>
        processClassDeclarations(clazz, BaseProcessor.this, state.withSubstitutor(newSubst), null, place)
      case des: ScTypedDefinition =>
        val typeResult: TypeResult =
          des match {
            case p: ScParameter => p.getRealParameterType
            case _ => des.`type`()
          }
        typeResult match {
          case Right(tp) =>
            val newState = state.withSubstitutor(ScSubstitutor.empty)
            processTypeImpl(newSubst(tp), place, newState, updateWithProjectionSubst = false)
          case _ => true
        }
      case pack: ScPackage =>
        pack.processDeclarations(BaseProcessor.this, state.withSubstitutor(newSubst), null, place)
      case des =>
        des.processDeclarations(BaseProcessor.this, state.withSubstitutor(newSubst), null, place)
    }
  }
}