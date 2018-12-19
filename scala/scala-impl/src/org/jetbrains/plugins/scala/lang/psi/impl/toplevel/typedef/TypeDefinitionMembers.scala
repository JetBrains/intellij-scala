package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.scope.{ElementClassHint, NameHint, PsiScopeProcessor}
import com.intellij.psi.util.PsiTreeUtil.isContextAncestor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI

/**
 * @author ven
 * @author alefas
 */
object TypeDefinitionMembers {

  //noinspection ScalaWrongMethodsUsage
  private def isStaticJava(m: PsiMember): Boolean = !m.isInstanceOf[ScalaPsiElement] && m.hasModifierProperty("static")

  object TypeNodes extends MixinNodes[PsiNamedElement] {
    def shouldSkip(t: PsiNamedElement): Boolean = t match {
      case _: ScObject => true
      case _: ScTypeDefinition | _: ScTypeAlias => false
      case c: PsiClass => isStaticJava(c)
      case _ => true
    }


    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (inner <- clazz.getInnerClasses) {
        addToMap(inner, new Node(inner, subst), map)
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (member <- template.members.filterBy[ScNamedElement]) {
        addToMap(member, new Node(member, subst), map)
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for ((_, TypeAliasSignature(_, _, _, _, _, alias)) <- cp.typesMap) {
        addToMap(alias, new Node(alias, ScSubstitutor.empty), map)
      }
    }
  }

  object SignatureNodes extends SignatureNodes

  //we need to have separate map for stable elements to avoid recursion processing declarations from imports
  object StableNodes extends SignatureNodes {

    override def shouldSkip(t: Signature): Boolean = !isStable(t.namedElement) || super.shouldSkip(t)

    private def isStable(named: PsiNamedElement): Boolean = named match {
      case _: ScObject => true
      case t: ScTypedDefinition => t.isStable
      case _ => false
    }
  }

  abstract class SignatureNodes extends MixinNodes[Signature] {
    def shouldSkip(t: Signature): Boolean = t.namedElement match {
      case f: ScFunction => f.isBridge || f.isConstructor
      case m: PsiMethod  => m.isConstructor || isStaticJava(m)
      case m: PsiMember  => isStaticJava(m)
      case _             => false
    }

    def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      for (method <- clazz.getMethods) {
        val phys = new PhysicalSignature(method, subst)
        addToMap(phys, new Node(phys, subst), map)
      }

      for (field <- clazz.getFields) {
        val sig = Signature.withoutParams(field.getName, subst, field)
        addToMap(sig, new Node(sig, subst), map)
      }
    }

    def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement]) {
      implicit val ctx: ProjectContext = template

      def addSignature(s: Signature) {
        addToMap(s, new Node(s, subst), map)
      }

      if (template.qualifiedName == "scala.AnyVal") {
        addAnyValObjectMethods(template, addSignature)
      }

      for (member <- template.members) {
        member match {
          case v: ScValueOrVariable =>
            v.declaredElements
              .foreach(addPropertySignatures(_, subst, addSignature))
          case constr: ScPrimaryConstructor =>
            constr.parameters
              .foreach(addPropertySignatures(_, subst, addSignature))
          case f: ScFunction =>
            addSignature(new PhysicalSignature(f, subst))
          case o: ScObject =>
            addSignature(Signature(o, subst))
          case c: ScTypeDefinition =>
            syntheticSignaturesFromInnerClass(c, subst)
              .foreach(addSignature)
          case _ =>
        }
      }
    }

    def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement]) {
      for ((sign, _) <- cp.signatureMap) {
        addToMap(sign, new Node(sign, sign.substitutor), map)
      }
    }

    private def addAnyValObjectMethods(template: ScTemplateDefinition, addSignature: Signature => Unit): Unit = {
      //some methods of java.lang.Object are available for value classes
      val javaObject = ScalaPsiManager.instance(template.projectContext)
        .getCachedClass(template.resolveScope, "java.lang.Object")

      for (obj <- javaObject; method <- obj.getMethods) {
        method.getName match {
          case "equals" | "hashCode" | "toString" =>
            addSignature(new PhysicalSignature(method, ScSubstitutor.empty))
          case _ =>
        }
      }
    }

    /**
      * @param named is class parameter, or part of ScValue or ScVariable
      * */
    private def addPropertySignatures(named: ScTypedDefinition, subst: ScSubstitutor, addSignature: Signature => Unit): Unit = {
      addSignature(Signature(named, subst))
      if (named.isVar) {
        addSignature(Signature.setter(named, subst))
      }
      named.nameContext match {
        case s: ScAnnotationsHolder =>
          val beanProperty = isBeanProperty(s, noResolve = true)
          val booleanBeanProperty = isBooleanBeanProperty(s, noResolve = true)
          if (beanProperty || booleanBeanProperty) {
            val name = named.name
            if (beanProperty) {
              addSignature(Signature.withoutParams(beanGetterName(name), subst, named))
            } else {
              addSignature(Signature.withoutParams(booleanGetterName(name), subst, named))
            }
            if (named.isVar) {
              addSignature(Signature(beanSetterName(name), Seq(() => named.`type`().getOrAny), subst, named))
            }
          }
        case _ =>
      }
    }

    private def syntheticSignaturesFromInnerClass(td: ScTypeDefinition, subst: ScSubstitutor): Seq[Signature] = {
      val companionSig = td.fakeCompanionModule.map(Signature(_, subst))

      val implicitClassFun = td match {
        case c: ScClass if c.hasModifierProperty("implicit") =>
          c.getSyntheticImplicitMethod.map(new PhysicalSignature(_, subst))
        case _ => None
      }

      companionSig.toList ::: implicitClassFun.toList
    }

    def forAllSignatureNodes(c: PsiClass)(action: Node => Unit): Unit = {
      withResponsibleUI {
        for {
          signature <- TypeDefinitionMembers.getSignatures(c).allFirstSeq()
          (_, node) <- signature
        } action(node)
      }
    }
  }

  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes.{Map => SMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
  import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}

  def getSignatures(clazz: PsiClass): SMap              = ScalaPsiManager.instance(clazz).SignatureNodesCache.cachedMap(clazz)
  def getStableSignatures(clazz: PsiClass): PMap = ScalaPsiManager.instance(clazz).StableNodesCache.cachedMap(clazz)
  def getTypes(clazz: PsiClass): TMap                   = ScalaPsiManager.instance(clazz).TypeNodesCache.cachedMap(clazz)

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    ScalaPsiManager.instance(tp.projectContext).getStableSignatures(tp, compoundTypeThisType)
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    ScalaPsiManager.instance(tp.projectContext).getTypes(tp, compoundTypeThisType)
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    ScalaPsiManager.instance(tp.projectContext).getSignatures(tp, compoundTypeThisType)
  }

  def getSelfTypeSignatures(clazz: PsiClass): SMap = {

    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clazzType = td.getTypeWithProjections().getOrAny
            selfType.glb(clazzType) match {
              case c: ScCompoundType =>
                getSignatures(c, Some(clazzType))
              case tp =>
                val cl = tp.extractClass.getOrElse(clazz)
                getSignatures(cl)
            }
          case _ =>
            getSignatures(clazz)
        }
      case _ => getSignatures(clazz)
    }
  }

  def getSelfTypeTypes(clazz: PsiClass): TMap = {
    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clazzType = td.getTypeWithProjections().getOrAny
            selfType.glb(clazzType) match {
              case c: ScCompoundType =>
                getTypes(c, Some(clazzType))
              case tp =>
                val cl = tp.extractClass.getOrElse(clazz)
                getTypes(cl)
            }
          case _ =>
            getTypes(clazz)
        }
      case _ => getTypes(clazz)
    }
  }

  //todo: this method requires refactoring
  def processDeclarations(clazz: PsiClass,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    if (BaseProcessor.isImplicitProcessor(processor) && !clazz.isInstanceOf[ScTemplateDefinition]) return true

    if (!privateProcessDeclarations(processor, state, lastParent, place, AllSignatures(clazz)))
      return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place))
      return false

    if (shouldProcessMethods(processor) && !processEnum(clazz, processor.execute(_, state)))
      return false

    true
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {

    if (!privateProcessDeclarations(processor, state, lastParent, place, AllSignatures(td), isSupers = true))
      return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place))
      return false

    true
  }

  def processDeclarations(comp: ScCompoundType,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          lastParent: PsiElement,
                          place: PsiElement): Boolean = {
    val compThisType = Option(state.get(BaseProcessor.COMPOUND_TYPE_THIS_TYPE_KEY)).flatten

    if (!privateProcessDeclarations(processor, state, lastParent, place, AllSignatures(comp, compThisType)))
      return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place))
      return false

    true
  }

  private trait SignatureMapsProvider {
    def allSignatures: MixinNodes.Map[Signature]
    def stable       : MixinNodes.Map[Signature]
    def types        : MixinNodes.Map[PsiNamedElement]
    def fromCompanion: MixinNodes.Map[Signature]
  }

  private object AllSignatures {

    def apply(c: PsiClass): SignatureMapsProvider =
      new ClassSignatures(c)

    def apply(comp: ScCompoundType, compoundTypeThisType: Option[ScType]): SignatureMapsProvider =
      new CompoundTypeSignatures(comp, compoundTypeThisType)

    private class ClassSignatures(c: PsiClass) extends SignatureMapsProvider {
      override def allSignatures: MixinNodes.Map[Signature] = getSignatures(c)

      override def stable: MixinNodes.Map[Signature] = getStableSignatures(c)

      override def types: MixinNodes.Map[PsiNamedElement] = getTypes(c)

      override def fromCompanion: MixinNodes.Map[Signature] = signaturesFromCompanion(c)
    }


    private class CompoundTypeSignatures(ct: ScCompoundType, compoundTypeThisType: Option[ScType]) extends SignatureMapsProvider {
      override def allSignatures: MixinNodes.Map[Signature] = getSignatures(ct, compoundTypeThisType)

      override def stable: MixinNodes.Map[Signature] = getStableSignatures(ct, compoundTypeThisType)

      override def types: MixinNodes.Map[PsiNamedElement] = getTypes(ct, compoundTypeThisType)

      override def fromCompanion: MixinNodes.Map[Signature] = MixinNodes.emptyMap[Signature]
    }
  }

  private def privateProcessDeclarations(processor: PsiScopeProcessor,
                                         state: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement,
                                         provider: SignatureMapsProvider,
                                         isSupers: Boolean = false
                                        ): Boolean = {

    val subst = Option(state.get(ScSubstitutor.key)).getOrElse(ScSubstitutor.empty)
    val nameHint = getNameHint(processor, state)

    val isScalaProcessor = processor.isInstanceOf[BaseProcessor]

    val processMethods = shouldProcessMethods(processor)
    val processMethodRefs = shouldProcessMethodRefs(processor)
    val processValsForScala = isScalaProcessor && shouldProcessVals(processor)
    val processOnlyStable = shouldProcessOnlyStable(processor)
    val isImplicitProcessor = BaseProcessor.isImplicitProcessor(processor)

    def process(named: PsiNamedElement, nodeSubstitutor: ScSubstitutor): Boolean = {
      if (named.isValid) {
        processor.execute(named, state.put(ScSubstitutor.key, nodeSubstitutor.followed(subst)))
      } else true
    }

    def processSignatureNode(node: MixinNodes.Node[Signature]): Boolean = {

      val named = node.info.namedElement

      named match {
        case m: PsiMethod if processMethods || processMethodRefs =>
          if (!process(m, node.substitutor))
            return false

        case p: ScClassParameter if processValsForScala && !p.isClassMember =>
          //this is member only for class scope
          val clazz = p.containingClass
          if (clazz != null && isContextAncestor(clazz, place, false)) {
            if (!process(p, node.substitutor))
              return false
          }

        case t: ScTypedDefinition if processValsForScala =>

          if (!process(t, node.substitutor))
            return false

          if (!isImplicitProcessor) {
            val iterator = syntheticPropertyMethods(nameHint, node.info).iterator
            while (iterator.hasNext) {
              if (!process(iterator.next(), node.substitutor))
                return false
            }
          }
        case e =>
          if (!process(e, node.substitutor))
            return false
      }
      true
    }

    def processTypeNode(node: MixinNodes.Node[PsiNamedElement]): Boolean = process(node.info, node.substitutor)

    val signatures =
      if (processOnlyStable) provider.stable else provider.allSignatures

    if (processMethods || processMethodRefs || processValsForScala) {
      val nodesIterator = signatures.nodesIterator(nameHint, isSupers, onlyImplicit = isImplicitProcessor)

      if (!nodesIterator.filtered(nameHint)(processSignatureNode))
        return false
    }

    //add object methods as static java methods
    if (processMethods && !isScalaProcessor) {
      val nodesIterator = provider.fromCompanion.nodesIterator(nameHint, isSupers)

      if (!nodesIterator.filtered(nameHint)(processSignatureNode))
        return false
    }

    if (shouldProcessTypes(processor) || shouldProcessJavaInnerClasses(processor)) {
      val iterator = provider.types.nodesIterator(nameHint, isSupers)

      if (!iterator.filtered(nameHint, mayProcessTypeSignature(processor, _))(processTypeNode))
        return false
    }

    true
  }

  import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._

  private def shouldProcessVals(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ =>
      val hint: ElementClassHint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)
  }

  private def shouldProcessMethods(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ =>
      val hint = processor.getHint(ElementClassHint.KEY)
      hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)
  }

  private def shouldProcessMethodRefs(processor: PsiScopeProcessor): Boolean = processor match {
    case BaseProcessor(kinds) => (kinds contains METHOD) || (kinds contains VAR) || (kinds contains VAL)
    case _ => true
  }

  private def shouldProcessTypes(processor: PsiScopeProcessor): Boolean = processor match {
    case b: BaseProcessor if b.isImplicitProcessor => false
    case BaseProcessor(kinds) => (kinds contains CLASS) || (kinds contains METHOD)
    case _ => false //important: do not process inner classes!
  }

  private def shouldProcessJavaInnerClasses(processor: PsiScopeProcessor): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) return false
    val hint = processor.getHint(ElementClassHint.KEY)
    hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)
  }

  private def shouldProcessOnlyStable(processor: PsiScopeProcessor): Boolean = {
    processor match {
      case BaseProcessor(kinds) =>
        !kinds.contains(METHOD) && !kinds.contains(VAR)
      case _ => false
    }
  }

  private def mayProcessTypeSignature(processor: PsiScopeProcessor, named: PsiNamedElement): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) true
    else named.isInstanceOf[ScTypeDefinition]
  }

  def processEnum(clazz: PsiClass, process: PsiMethod => Boolean): Boolean = {
    var containsValues = false
    if (clazz.isEnum && !clazz.isInstanceOf[ScTemplateDefinition]) {
      containsValues = clazz.getMethods.exists {
        method =>
          method.getName == "values" && method.getParameterList.getParametersCount == 0 && isStaticJava(method)
      }
    }

    if (!containsValues && clazz.isEnum) {
      val elementFactory: PsiElementFactory = JavaPsiFacade.getInstance(clazz.getProject).getElementFactory
      //todo: cache like in PsiClassImpl
      val valuesMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
        "[] values() {}", clazz)
      val valueOfMethod: PsiMethod = elementFactory.createMethodFromText("public static " + clazz.name +
        " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException {}", clazz)
      val values = new LightMethod(clazz.getManager, valuesMethod, clazz)
      val valueOf = new LightMethod(clazz.getManager, valueOfMethod, clazz)

      if (!process(values))
        return false

      if (!process(valueOf))
        return false
    }
    true
  }

  private def signaturesFromCompanion(clazz: PsiClass): SignatureNodes.Map = {
    clazz match {
      case td: ScTypeDefinition =>
        getCompanionModule(td) match {
          case Some(obj: ScObject) => getSignatures(obj)
          case None => MixinNodes.emptyMap
        }
      case _ => MixinNodes.emptyMap
    }
  }

  private def processSyntheticAnyRefAndAny(processor: PsiScopeProcessor,
                                           state: ResolveState,
                                           lastParent: PsiElement,
                                           place: PsiElement): Boolean = {
    implicit val context: ProjectContext = place

    processSyntheticClass(api.AnyRef, processor, state, lastParent, place) &&
      processSyntheticClass(api.Any, processor, state, lastParent, place)
  }

  private def processSyntheticClass(stdType: StdType,
                                    processor: PsiScopeProcessor,
                                    state: ResolveState,
                                    lastParent: PsiElement,
                                    place: PsiElement): Boolean = {
    stdType.syntheticClass.forall(_.processDeclarations(processor, state, lastParent, place))
  }

  private def getNameHint(processor: PsiScopeProcessor, state: ResolveState): String = {
    val hint = processor.getHint(NameHint.KEY)
    val name = if (hint == null) "" else hint.getName(state)

    ScalaNamesUtil.clean(if (name != null) name else "")
  }

  private def syntheticPropertyMethods(nameHint: String, signature: Signature): Seq[PsiMethod] = {
    val sigName = signature.name

    signature.namedElement match {
      case t: ScTypedDefinition if isProperty(t) =>
         if (nameHint.isEmpty) getPropertyMethod(t, EQ) ++: getBeanMethods(t)
         else methodRole(sigName, t.name).flatMap(getPropertyMethod(t, _)).toSeq
      case _ => Seq.empty
    }
  }

  private implicit class SignatureNodeIteratorOps(override val iterator: Iterator[MixinNodes.Node[Signature]]) extends AnyVal
    with NodesIteratorFilteredOps[Signature] {

    protected def checkName(s: Signature, nameHint: String): Boolean =
      nameHint.isEmpty || s.name == nameHint || syntheticPropertyMethods(nameHint, s).nonEmpty
  }

  private implicit class TypeNodeIteratorOps(override val iterator: Iterator[MixinNodes.Node[PsiNamedElement]]) extends AnyVal
    with NodesIteratorFilteredOps[PsiNamedElement] {

    protected def checkName(named: PsiNamedElement, nameHint: String): Boolean =
      nameHint.isEmpty || ScalaNamesUtil.clean(named.name) == nameHint
  }

  private trait NodesIteratorFilteredOps[T] extends Any {
    def iterator: Iterator[MixinNodes.Node[T]]

    def filtered(name: String, condition: T => Boolean = Function.const(true))
                (action: MixinNodes.Node[T] => Boolean): Boolean = {
      while (iterator.hasNext) {
        val n = iterator.next()
        if (checkName(n.info, name) && condition(n.info)) {
          ProgressManager.checkCanceled()
          if (!action(n))
            return false
        }
      }
      true
    }

    protected def checkName(t: T, nameHint: String): Boolean
  }
}