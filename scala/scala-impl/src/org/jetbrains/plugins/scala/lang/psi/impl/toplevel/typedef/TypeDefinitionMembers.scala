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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.isContextAncestor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.{util => ju}

object TypeDefinitionMembers {

  def isValSignature(signature: TermSignature): Boolean = signature match {
    case _: PhysicalMethodSignature => false
    case _ =>
      val element = signature.namedElement
      element.nameContext match {
        case _: ScValueOrVariable |
             _: ScClassParameter => element.name == signature.name
        case _: PsiField => true
        case _ => false
      }
  }

  object TypeNodes extends MixinNodes[TypeSignature](TypesCollector)

  object TermNodes extends MixinNodes[TermSignature](TermsCollector)

  //we need to have separate map for stable elements to avoid recursion processing declarations from imports
  object StableNodes extends MixinNodes[TermSignature](StableTermsCollector)

  def getSignatures(clazz: PsiClass, withSupers: Boolean): TermNodes.Map =
    ifValid(clazz)(_.TermNodesCache.cachedMap(clazz, withSupers))

  def getStableSignatures(clazz: PsiClass, withSupers: Boolean): StableNodes.Map =
    ifValid(clazz)(_.StableNodesCache.cachedMap(clazz, withSupers))

  def getTypes(clazz: PsiClass, withSupers: Boolean): TypeNodes.Map =
    ifValid(clazz)(_.TypeNodesCache.cachedMap(clazz, withSupers))

  def getSignatures(clazz: PsiClass): TermNodes.Map =
    getSignatures(clazz, withSupers = true)

  def getStableSignatures(clazz: PsiClass): StableNodes.Map =
    getStableSignatures(clazz, withSupers = true)

  def getTypes(clazz: PsiClass): TypeNodes.Map =
    getTypes(clazz, withSupers = true)

  private def ifValid[T <: Signature](clazz: PsiClass)
                                     (cache: ScalaPsiManager => MixinNodes[T]#Map): MixinNodes[T]#Map = {
    clazz match {
      case Valid(c) =>
        val manager = ScalaPsiManager.instance(c.getProject)
        cache(manager)
      case _ => MixinNodes.emptyMap
    }
  }

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): StableNodes.Map = {
    ScalaPsiManager.instance(tp.projectContext).getStableSignatures(tp, compoundTypeThisType)
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TypeNodes.Map = {
    ScalaPsiManager.instance(tp.projectContext).getTypes(tp, compoundTypeThisType)
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TermNodes.Map = {
    ScalaPsiManager.instance(tp.projectContext).getSignatures(tp, compoundTypeThisType)
  }

  def getSelfTypeSignatures(clazz: PsiClass): TermNodes.Map = {
    @annotation.tailrec
    def extractFromThisType(clsType: ScType, thisType: ScType): TermNodes.Map = thisType match {
      case c: ScCompoundType       => getSignatures(c, Option(clsType))
      case ScExistentialType(q, _) => extractFromThisType(clsType, q)
      case tp =>
        val cls = tp.extractClass.getOrElse(clazz)
        getSignatures(cls)
    }

    clazz match {
      case td: ScTypeDefinition =>
        td.selfType match {
          case Some(selfType) =>
            val clsType  = td.getTypeWithProjections().getOrAny
            val thisType = selfType.glb(clsType)
            extractFromThisType(clsType, thisType)
          case None => getSignatures(clazz)
        }
      case _ => getSignatures(clazz)
    }
  }

  def getSelfTypeTypes(clazz: PsiClass): TypeNodes.Map = {
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

  /** Take extra care to avoid reentrancy issues when processing package objects */
  private[this] val processing: UnloadableThreadLocal[ju.Map[String, java.lang.Long]] = UnloadableThreadLocal(new ju.HashMap)

  private[this] def checkPackageObjectReentrancy(fqn: String): Boolean =
    processing.value.getOrDefault(fqn, 0L) != 0L

  private[this] def withReentrancyGuard(fqn: String)(action: => Boolean): Boolean =
    try {
      processing.value.merge(fqn, 1L, (old, _) => old + 1)
      action
    } finally {
      processing.value.merge(fqn, 0L, (old, _) => if (old == 1L) null else old - 1)
    }

  def processClassDeclarations(
    clazz:      PsiClass,
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (BaseProcessor.isImplicitProcessor(processor) && !clazz.is[ScTemplateDefinition]) return true

    val pkgObjectFqn = clazz match {
      case obj: ScObject if obj.isPackageObject => Option(obj.qualifiedName)
      case _                                    => None
    }

    val isPackageObject          = pkgObjectFqn.isDefined
    val isReentrantPackageObject = pkgObjectFqn.exists(checkPackageObjectReentrancy)

    val signatures =
      if (isReentrantPackageObject) AllSignatures.NonInherited(clazz)
      else                          AllSignatures(clazz)

    def processDeclarationsInner(): Boolean = privateProcessDeclarations(processor, state, place, signatures)

    val processDeclsResult =
      if (isPackageObject) withReentrancyGuard(pkgObjectFqn.get)(processDeclarationsInner())
      else                 processDeclarationsInner()

    if (!processDeclsResult)
      return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place))
      return false

    if (shouldProcessMethods(processor) && !processEnum(clazz, processor.execute(_, state)))
      return false

    if (place.isInScala3File)
      stdLibPatches(clazz).foreach {
        processClassDeclarations(_, processor, state, lastParent, place)
      }

    true
  }

  def processSuperDeclarations(td: ScTemplateDefinition,
                               processor: PsiScopeProcessor,
                               state: ResolveState,
                               lastParent: PsiElement,
                               place: PsiElement): Boolean = {

    if (!privateProcessDeclarations(processor, state, place, AllSignatures(td), isSupers = true))
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

    if (!privateProcessDeclarations(processor, state, place, AllSignatures(comp, state.compoundOrThisType)))
      return false

    if (!processSyntheticAnyRefAndAny(processor, state, lastParent, place))
      return false

    true
  }

  private trait SignatureMapsProvider {
    def allSignatures: MixinNodes.Map[TermSignature]
    def stable       : MixinNodes.Map[TermSignature]
    def types        : MixinNodes.Map[TypeSignature]
    def fromCompanion: MixinNodes.Map[TermSignature]
  }

  private object AllSignatures {
    object NonInherited {
      def apply(c: PsiClass): SignatureMapsProvider =
        new ClassSignatures(c, withSupers = false)
    }

    def apply(c: PsiClass): SignatureMapsProvider =
      new ClassSignatures(c, withSupers = true)

    def apply(comp: ScCompoundType, compoundTypeThisType: Option[ScType]): SignatureMapsProvider =
      new CompoundTypeSignatures(comp, compoundTypeThisType)

    private class ClassSignatures(c: PsiClass, withSupers: Boolean) extends SignatureMapsProvider {
      override def allSignatures: MixinNodes.Map[TermSignature] = getSignatures(c, withSupers)

      override def stable: MixinNodes.Map[TermSignature] = getStableSignatures(c, withSupers)

      override def types: MixinNodes.Map[TypeSignature] = getTypes(c, withSupers)

      override def fromCompanion: MixinNodes.Map[TermSignature] = signaturesFromCompanion(c, withSupers)
    }


    private class CompoundTypeSignatures(ct: ScCompoundType, compoundTypeThisType: Option[ScType]) extends SignatureMapsProvider {
      override def allSignatures: MixinNodes.Map[TermSignature] = getSignatures(ct, compoundTypeThisType)

      override def stable: MixinNodes.Map[TermSignature] = getStableSignatures(ct, compoundTypeThisType)

      override def types: MixinNodes.Map[TypeSignature] = getTypes(ct, compoundTypeThisType)

      override def fromCompanion: MixinNodes.Map[TermSignature] = MixinNodes.emptyMap[TermSignature]
    }
  }

  private def privateProcessDeclarations(
    processor: PsiScopeProcessor,
    state:     ResolveState,
    place:     PsiElement,
    provider:  SignatureMapsProvider,
    isSupers:  Boolean = false
  ): Boolean = {

    val subst               = state.substitutor
    val nameHint            = getNameHint(processor, state)
    val isScalaProcessor    = processor.is[BaseProcessor]
    val processMethods      = shouldProcessMethods(processor)
    val processMethodRefs   = shouldProcessMethodRefs(processor)
    val processValsForScala = isScalaProcessor && shouldProcessVals(processor)
    val processOnlyStable   = ProcessorUtils.shouldProcessOnlyStable(processor)
    val isImplicitProcessor = BaseProcessor.isImplicitProcessor(processor)

    def process(signature: Signature): Boolean = {
      if (signature.namedElement.isValid) {
        val withSubst   = state.withSubstitutor(signature.substitutor.followed(subst))
        val withRenamed = withSubst.withRename(signature.renamed)
        processor.execute(signature.namedElement, withRenamed)
      } else true
    }


    def processTermNode(node: MixinNodes.Node[TermSignature]): Boolean = {

      val named = node.info.namedElement

      named match {
        case _: PsiMethod if processMethods || processMethodRefs =>
          if (!process(node.info))
            return false

        case p: ScClassParameter if processValsForScala && !p.isClassMember =>
          //this is member only for class scope
          val clazz = p.containingClass

          val isAccesible = clazz != null && isContextAncestor(clazz, place, false) && {
            //enum constructor parameters cannot be accessed inside enum cases
            !clazz.is[ScEnum] ||
              PsiTreeUtil.getContextOfType(place, classOf[ScEnumCase], classOf[ScEnum]) == clazz
          }

          if (isAccesible) {
            if (!process(node.info))
              return false
          }

        case _: ScTypedDefinition if processValsForScala =>

          if (!process(node.info))
            return false

          if (!isImplicitProcessor) {
            val iterator = syntheticPropertyMethods(nameHint, node.info).iterator
            while (iterator.hasNext) {
              if (!process(iterator.next()))
                return false
            }
          }
        case _ =>
          if (!process(node.info))
            return false
      }
      true
    }

    def processTypeNode(node: MixinNodes.Node[TypeSignature]): Boolean = process(node.info)

    val signatures =
      if (processOnlyStable) provider.stable
      else                   provider.allSignatures

    if (processMethods || processMethodRefs || processValsForScala) {
      val nodesIterator = signatures.nodesIterator(nameHint, isSupers, onlyImplicit = isImplicitProcessor)

      if (!nodesIterator.filtered(nameHint)(processTermNode))
        return false
    }

    //add object methods as static java methods
    if (processMethods && !isScalaProcessor) {
      val nodesIterator = provider.fromCompanion.nodesIterator(nameHint, isSupers)

      if (!nodesIterator.filtered(nameHint)(processTermNode))
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

  private def mayProcessTypeSignature(processor: PsiScopeProcessor, typeSignature: TypeSignature): Boolean = {
    if (processor.isInstanceOf[BaseProcessor]) true
    else typeSignature.namedElement.isInstanceOf[ScTypeDefinition]
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

  private def signaturesFromCompanion(clazz: PsiClass, withSupers: Boolean): TermNodes.Map = {
    clazz match {
      case td: ScTypeDefinition =>
        getCompanionModule(td) match {
          case Some(obj: ScObject) => getSignatures(obj, withSupers)
          case _                   => MixinNodes.emptyMap
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

  private def syntheticPropertyMethods(nameHint: String, signature: TermSignature): Seq[TermSignature] = {
    val sigName = signature.name

    val syntheticMethods = signature.namedElement match {
      case t: ScTypedDefinition if isProperty(t) =>
         if (nameHint.isEmpty) getPropertyMethod(t, EQ) ++: getBeanMethods(t)
         else methodRole(sigName, t.name).flatMap(getPropertyMethod(t, _)).toSeq
      case _ => Seq.empty
    }
    syntheticMethods.map(TermSignature(_, signature.substitutor))
  }

  private object stdLibPatches {
    val map = Map(
      "scala.Predef" -> "scala.runtime.stdLibPatches.Predef",
      "scala.language" -> "scala.runtime.stdLibPatches.language"
    )

    def apply(clazz: PsiClass): Option[ScObject] =
      for {
        obj       <- clazz.asOptionOf[ScObject]
        patchName <- map.get(obj.qualifiedName)
        patchObj  <- clazz.elementScope.getCachedObject(patchName)
      } yield patchObj

  }


  private implicit class TermNodeIteratorOps(override val iterator: Iterator[MixinNodes.Node[TermSignature]]) extends AnyVal
    with NodesIteratorFilteredOps[TermSignature] {

    override protected def checkName(s: TermSignature, nameHint: String): Boolean =
      nameHint.isEmpty || s.name == nameHint || syntheticPropertyMethods(nameHint, s).nonEmpty
  }

  private implicit class TypeNodeIteratorOps(override val iterator: Iterator[MixinNodes.Node[TypeSignature]]) extends AnyVal
    with NodesIteratorFilteredOps[TypeSignature] {

    override protected def checkName(named: TypeSignature, nameHint: String): Boolean =
      nameHint.isEmpty || ScalaNamesUtil.clean(named.name) == nameHint
  }

  private trait NodesIteratorFilteredOps[T <: Signature] extends Any {
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