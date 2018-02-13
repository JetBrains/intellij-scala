package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor.RecursionState
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.{Set, mutable}

object BaseProcessor {
  def unapply(p: BaseProcessor) = Some(p.kinds)

  val boundClassKey: Key[PsiClass] = Key.create("bound.class.key")

  val FROM_TYPE_KEY: Key[ScType] = Key.create("from.type.key")

  val UNRESOLVED_TYPE_PARAMETERS_KEY: Key[Seq[TypeParameter]] = Key.create("unresolved.type.parameters.key")

  val COMPOUND_TYPE_THIS_TYPE_KEY: Key[Option[ScType]] = Key.create("compound.type.this.type.key")

  val FORWARD_REFERENCE_KEY: Key[java.lang.Boolean] = Key.create("forward.reference.key")

  def isImplicitProcessor(processor: PsiScopeProcessor): Boolean = {
    processor match {
      case b: BaseProcessor => b.isImplicitProcessor
      case _ => false
    }
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
  protected val candidatesSet: mutable.HashSet[ScalaResolveResult] = new mutable.HashSet[ScalaResolveResult]

  def isImplicitProcessor: Boolean = false

  def changedLevel: Boolean = true

  protected var accessibility = true
  def doNotCheckAccessibility() {accessibility = false}

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
  def setClassKind(classKind: Boolean) {
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
    def shouldProcess(kind: DeclarationKind): Boolean = {
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

  protected def kindMatches(element: PsiElement): Boolean = ResolveUtils.kindMatches(element, kinds)

  def processType(t: ScType, place: PsiElement, state: ResolveState = ResolveState.initial()): Boolean =
    processTypeImpl(t, place, state)(RecursionState.empty)

  private def processTypeImpl(t: ScType, place: PsiElement,
                              state: ResolveState = ResolveState.initial(),
                              updateWithProjectionSubst: Boolean = true)
                             (implicit recState: RecursionState): Boolean = {
    ProgressManager.checkCanceled()

    t match {
      case ScDesignatorType(clazz: PsiClass) if clazz.qualifiedName == "java.lang.String" =>
        val plusMethod: ScType => ScSyntheticFunction = SyntheticClasses.get(place.getProject).stringPlusMethod
        if (plusMethod != null) execute(plusMethod(t), state) //add + method
      case _ =>
    }

    t match {
      case ScThisType(clazz) =>
        if (clazz.selfType.isEmpty) {
          processElement(clazz, ScSubstitutor.empty, place, state)
        } else {
          val selfType = clazz.selfType.get
          val clazzType: ScType = clazz.getTypeWithProjections().getOrElse(return true)
          if (selfType == ScThisType(clazz)) {
            //to prevent SOE, let's process Element
            processElement(clazz, ScSubstitutor.empty, place, state)
          } else if (selfType.conforms(clazzType)) {
            processTypeImpl(selfType, place, state.put(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY, Some(t)).
              put(ScSubstitutor.key, ScSubstitutor(ScThisType(clazz))))
          } else if (clazzType.conforms(selfType)) {
            processElement(clazz, ScSubstitutor.empty, place, state)
          } else {
            processTypeImpl(clazz.selfType.map(_.glb(clazzType)).get, place,
              state.put(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY, Some(t)))
          }
        }
      case d@ScDesignatorType(e: PsiClass) if d.asInstanceOf[ScDesignatorType].isStatic && !e.isInstanceOf[ScTemplateDefinition] =>
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
        TypeDefinitionMembers.processEnum(e, execute(_, state))
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
      case ScDesignatorType(e) =>
        processElement(e, ScSubstitutor.empty, place, state)
      case TypeParameterType(Nil, _, upper, _) =>
        processTypeImpl(upper, place, state, updateWithProjectionSubst = false)
      case j: JavaArrayType =>
        implicit val elementScope = place.elementScope
        processTypeImpl(j.getParameterizedType.getOrElse(return true), place, state)
      case p@ParameterizedType(designator, typeArgs) =>
        designator match {
          case tpt@TypeParameterType(_, _, upper, _) =>
            if (recState.visitedTypeParameter.contains(tpt)) return true
            val newState = state.put(ScSubstitutor.key, ScSubstitutor(p))
            val substedType = p.substitutor.subst(ParameterizedType(upper, typeArgs))
            processTypeImpl(substedType, place, newState)(recState.add(tpt))
          case _ => p.extractDesignatedType(expandAliases = false) match {
            case Some((des, subst)) =>
              processElement(des, subst, place, state)
            case None => true
          }
        }
      case proj@ScProjectionType(_, _) if proj.actualElement.isInstanceOf[ScTypeAlias] =>
        val ta = proj.actualElement.asInstanceOf[ScTypeAlias]
        val subst = proj.actualSubst
        val upper = ta.upperBound.getOrElse(return true)
        processTypeImpl(subst.subst(upper), place, state.put(ScSubstitutor.key, ScSubstitutor.empty))(recState.add(ta))
      case proj@ScProjectionType(_, _) =>
        val s: ScSubstitutor =
          if (updateWithProjectionSubst)
            ScSubstitutor(proj) followed proj.actualSubst
          else proj.actualSubst
        val actualElement = proj.actualElement
        processElement(actualElement, s, place, state)(recState.add(actualElement))
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
      case comp@ScCompoundType(_, _, _) =>
        TypeDefinitionMembers.processDeclarations(comp, this, state, null, place)
      case ex: ScExistentialType =>
        processTypeImpl(ex.quantified, place, state.put(ScSubstitutor.key, ScSubstitutor.empty))
      case ScExistentialArgument(_, _, _, upper) =>
        processTypeImpl(upper, place, state)
      case _ => true
    }
  }

  private def processElement(e: PsiNamedElement, s: ScSubstitutor, place: PsiElement, state: ResolveState)
                            (implicit recState: RecursionState): Boolean = {
    val subst = state.get(ScSubstitutor.key)
    val compound = state.get(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY) //todo: looks like ugly workaround
    val newSubst =
      compound match {
        case Some(_) => subst
        case _ => if (subst != null) subst followed s else s
      }
    e match {
      case ta: ScTypeAlias =>
        if (recState.visitedProjections.contains(ta)) return true
        val newState = state.put(ScSubstitutor.key, ScSubstitutor.empty)
        processTypeImpl(s.subst(ta.upperBound.getOrAny), place, newState)(recState.add(ta))
      //need to process scala way
      case clazz: PsiClass =>
        TypeDefinitionMembers.processDeclarations(clazz, BaseProcessor.this, state.put(ScSubstitutor.key, newSubst), null, place)
      case des: ScTypedDefinition =>
        val typeResult: TypeResult =
          des match {
            case p: ScParameter => p.getRealParameterType
            case _ => des.`type`()
          }
        typeResult match {
          case Right(tp) =>
            val newState = state.put(ScSubstitutor.key, ScSubstitutor.empty)
            processTypeImpl(newSubst subst tp, place, newState, updateWithProjectionSubst = false)
          case _ => true
        }
      case pack: ScPackage =>
        pack.processDeclarations(BaseProcessor.this, state.put(ScSubstitutor.key, newSubst), null, place)
      case des =>
        des.processDeclarations(BaseProcessor.this, state.put(ScSubstitutor.key, newSubst), null, place)
    }
  }

  protected def getSubst(state: ResolveState): ScSubstitutor = {
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

  protected def isForwardReference(state: ResolveState): Boolean = {
    val res: java.lang.Boolean = state.get(BaseProcessor.FORWARD_REFERENCE_KEY)
    if (res != null) res
    else false
  }
}
