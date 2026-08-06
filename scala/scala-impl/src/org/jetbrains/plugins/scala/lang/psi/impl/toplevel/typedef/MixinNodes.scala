package org.jetbrains.plugins.scala.lang.psi
package impl
package toplevel
package typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiClassType, PsiMethod, PsiNamedElement}
import com.intellij.util.containers.{ContainerUtil, SmartHashSet}
import com.intellij.util.{AstLoadingFilter, SmartList}
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.{Object2ObjectMap, Object2ObjectMaps, Object2ObjectOpenCustomHashMap}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes.{MapImpl, SourceKind, SuperTypesData, extractClassOrUpperBoundClass}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.util.{ScEquivalenceUtil, UnloadableThreadLocal}

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{HashMap => JMap, List => JList}
import scala.annotation.tailrec
import scala.collection.immutable.{ArraySeq, SeqMap}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Internal builder for merged member-signature graphs used by resolve, override navigation, and completion.
 *
 * [[MixinNodes]] consumes signatures produced by a [[SignatureProcessor]] and builds a name-indexed map with
 * inheritance links ([[MixinNodes.Node]]). It is instantiated for different signature kinds
 * (for example, term, stable-term, and type signatures) by [[TypeDefinitionMembers]].
 *
 * Build flow:
 *   - collect inherited members (nominal supers and, for compound- / self-types, self-type-derived supers)
 *   - collect declared/refinement members
 *   - merge equivalent signatures and attach super links
 *
 * Entry points are [[MixinNodes.build]] (all overloaded versions)
 *
 * This type is implementation API (`impl.toplevel.typedef`), not PSI model API.
 */
abstract class MixinNodes[T <: Signature](signatureCollector: SignatureProcessor[T]) {
  type Map = MixinNodes.Map[T]

  def build(clazz: PsiClass, withSupers: Boolean, subst: ScSubstitutor = ScSubstitutor.empty): Map = {
    if (!clazz.isValid) MixinNodes.emptyMap[T]
    else {
      def build0: MapImpl[T] = {
        val templateDefinitionOpt = Option(clazz).collect { case td: ScTemplateDefinition => td }
        val map = new MapImpl[T](templateDefinitionOpt)

        if (withSupers) {
          addSuperSignatures(SuperTypesData(clazz), map, SourceKind.NominalSuper)
        }

        map.supersFinished(templateDefinitionOpt)

        signatureCollector.processPsiClass(clazz, subst, map)
        map.sigsFinished()

        map
      }

      if (ScalaApplicationSettings.PRECISE_TEXT) { // SCL-21199
        build0
      } else {
        AstLoadingFilter.disallowTreeLoading(() => build0, () => "Tree access is disallowed in MixinNodes.build")
      }
    }
  }

  def build(andTpe: ScAndType): Map = {
    def collectChildrenNodes(tp: ScType): Seq[Map] = tp match {
      case ScAndType(lhs, rhs)  => collectChildrenNodes(lhs) ++ collectChildrenNodes(rhs)
      case comp: ScCompoundType => Seq(build(comp, None))
      case other                =>
        extractClassOrUpperBoundClass(other) match {
          case Some((cls, subst)) => Seq(build(cls, withSupers = true, subst = subst))
          case _                  => Seq.empty
        }
    }

    val maps = collectChildrenNodes(andTpe)
    maps.fold(MixinNodes.emptyMap)(_ intersect _)
  }

  def build(cp: ScCompoundType, compoundThisType: Option[ScType]): Map = {
    val map = new MapImpl[T](None)

    // Keep the refinement in both halves of the member graph, as before signature-origin tracking was added.
    // Refinement members are type-level requirements, not implementation sources.
    map.useSource(SourceKind.RefinementSuper)
    cp match {
      case comp: ScCompoundType => signatureCollector.processRefinement(comp, map)
      case _                    => ()
    }

    // Compound/self-type expansion contributes inherited members applicable via self-type constraints.
    addSuperSignatures(SuperTypesData(cp, compoundThisType), map, SourceKind.SelfTypeSuper)
    map.supersFinished()

    map.useSource(SourceKind.Refinement)
    signatureCollector.processRefinement(cp, map)
    map.sigsFinished()
    map
  }

  private def addSuperSignatures(superTypesData: SuperTypesData, map: MapImpl[T], sourceKind: SourceKind): Unit = {
    map.useSource(sourceKind)

    for ((superClass, subst) <- superTypesData.substitutors) {
      signatureCollector.processPsiClass(superClass, subst, map)
    }

    for (compoundType <- superTypesData.refinements) {
      signatureCollector.processRefinement(compoundType, map)
    }
  }
}

/**
 * Companion internals for [[MixinNodes]]:
 *   - signature-origin model ([[SourceKind]])
 *   - node/map containers and merge utilities
 *   - type linearization and "as seen from" substitution helpers
 *
 * This object centralizes algorithms that transform raw collected signatures into inheritance-aware lookup structures.
 */
object MixinNodes {
  /**
   * Describes how a signature node entered the merged signatures map.
   *
   * The signature origin distinguishes source-level implementation members from inherited or type-level members.
   * In particular, a member supplied through a satisfied self-type is a valid implementation source, while a
   * nominally inherited, refinement, or refinement-super member is not.
   */
  sealed trait SourceKind {
    /**
     * Whether the signature was collected while processing a supertype or a refinement-super pass.
     */
    final def fromSuper: Boolean = this match {
      case SourceKind.NominalSuper | SourceKind.SelfTypeSuper | SourceKind.RefinementSuper => true
      case SourceKind.Declared | SourceKind.Exported | SourceKind.Refinement => false
    }

    /**
     * Whether the signature may be reported as a source-level implementation by the overriding-member search.
     */
    final def isAllowedImplementationSource: Boolean = this match {
      case SourceKind.Declared | SourceKind.Exported | SourceKind.SelfTypeSuper => true
      case SourceKind.NominalSuper | SourceKind.RefinementSuper | SourceKind.Refinement => false
    }
  }

  object SourceKind {
    /** Members declared directly in the current class or template. */
    case object Declared extends SourceKind

    /** Members exposed by an export clause declared in the current class or template. */
    case object Exported extends SourceKind

    /** Members inherited through the nominal parent-type linearization. */
    case object NominalSuper extends SourceKind

    /** Members supplied by a template whose self-type is satisfied by the current class. */
    case object SelfTypeSuper extends SourceKind

    /** Members collected from a refinement during the super-signature pass. */
    case object RefinementSuper extends SourceKind

    /** Members declared by the final refinement pass of a compound type. */
    case object Refinement extends SourceKind

    /**
     * Combines signature origins when conflicting inherited signatures are represented by one intersected node.
     *
     * [[AllNodes.merge]] can combine signatures from different branches of a compound or intersection type.
     * The resulting node must retain [[SelfTypeSuper]] if either contribution came through a satisfied self-type,
     * because that origin makes the member eligible for implementation search. If neither contribution has
     * that origin, the combined signature is treated as an ordinary inherited member and remains ineligible.
     * The self-type signature origin therefore takes precedence; otherwise the result is [[NominalSuper]].
     */
    def mergedSuperKind(left: SourceKind, right: SourceKind): SourceKind =
      if (left == SelfTypeSuper || right == SelfTypeSuper) SelfTypeSuper
      else NominalSuper
  }

  val currentlyProcessedSigs: UnloadableThreadLocal[JMap[PsiClass, Map[TermSignature]]] =
    new UnloadableThreadLocal(new JMap)

  def withSignaturesFor[T](cls: PsiClass, sigs: Map[TermSignature])(f: =>T): T = try {
    currentlyProcessedSigs.value.put(cls, sigs)
    f
  } finally {
    currentlyProcessedSigs.value.remove(cls)
  }


  private case class SuperTypesData(substitutors: SeqMap[PsiClass, ScSubstitutor], refinements: Seq[ScCompoundType])

  private object SuperTypesData {

    def apply(thisClass: PsiClass): SuperTypesData =
      cachedInUserData("SuperTypesData.apply", thisClass, ModTracker.libraryAware(thisClass), Tuple1(thisClass)) {
        val superTypes = thisClass match {
          case syn: ScSyntheticClass          => syn.getSuperTypes.map(_.toScType()(syn)).toSeq
          case newTd: ScNewTemplateDefinition => MixinNodes.linearization(newTd)
          case _                              => MixinNodes.linearization(thisClass).drop(1)
        }

        val thisType = thisClass match {
          case td: ScTemplateDefinition => ScThisType(td)
          case _                        => null
        }

        SuperTypesData(superTypes, thisType)
      }

    def apply(cp: ScCompoundType, compoundThisType: Option[ScType])(implicit context: Context): SuperTypesData = {
      val superTypes = MixinNodes.linearization(cp)
      val thisType = compoundThisType.getOrElse(cp)
      SuperTypesData(superTypes, thisType)
    }

    private def apply(superTypes: Seq[ScType], @Nullable thisType: ScType): SuperTypesData = {
      val substitutorsBuilder = SeqMap.newBuilder[PsiClass, ScSubstitutor]
      val refinementsBuilder = List.newBuilder[ScCompoundType]

      for (superType <- superTypes) {
        superType.extractClassType match {
          case Some((superClass, s)) =>
            val seenFromClass = if (thisType == null) null else superClass
            val dependentSubst = superType match {
              case p @ ScProjectionType(proj, _)                       => ScSubstitutor(proj, seenFromClass).followed(p.actualSubst)
              case ParameterizedType(p @ ScProjectionType(proj, _), _) => ScSubstitutor(proj, seenFromClass).followed(p.actualSubst)
              case _                                                   => ScSubstitutor.empty
            }
            val thisTypeSubst = if (thisType == null) ScSubstitutor.empty else ScSubstitutor(thisType, seenFromClass)
            val newSubst = combine(s, superClass).followed(thisTypeSubst).followed(dependentSubst)
            substitutorsBuilder += superClass -> newSubst
          case _ =>
            dealias(superType) match {
              case cp: ScCompoundType =>
                refinementsBuilder += cp
              case _ =>
            }
        }
      }
      SuperTypesData(substitutorsBuilder.result(), refinementsBuilder.result())
    }

    private def combine(superSubst: ScSubstitutor, superClass: PsiClass): ScSubstitutor = {
      val typeParameters = superClass.getTypeParameters
      val substedTpts = typeParameters.map(tp => superSubst(TypeParameterType(tp)))
      ScSubstitutor.bind(typeParameters, substedTpts)
    }
  }

  def allSuperClassesWithSubst(cls: PsiClass): SeqMap[PsiClass, ScSubstitutor] =
    SuperTypesData(cls).substitutors

  def allSuperClasses(clazz: PsiClass): Set[PsiClass] =
    SuperTypesData(clazz).substitutors.keys.toSet

  def asSeenFromSubstitutor(superClass: PsiClass, thisClass: PsiClass): ScSubstitutor =
    SuperTypesData(thisClass).substitutors.getOrElse(superClass, ScSubstitutor.empty)

  /**
   * Internal graph node used by [[MixinNodes]] to connect merged signatures across inheritance.
   *
   * == Difference with [[types.Signature]] ==
   *  - [[types.Signature]] models member semantics and equivalence<br>
   *    (declaration origin, visible identity, type adaptation, etc.)
   *  - This `Node` wraps such semantic member info and adds inheritance-graph topology:
   *    - `fromSuper`: whether the represented member originated from a base type during collection
   *    - `supers`: all equivalent inherited candidates linked during merge
   *    - `primarySuper`: preferred inherited candidate (concrete first, then first available)
   *
   * The [[info]] stores collected member metadata (usually a [[types.Signature]]).
   * The added topology is used for super-member navigation and override resolution.
   *
   * Unlike [[types.Signature]], this type is not a PSI-level semantic model.
   * It is an implementation detail used inside [[impl.toplevel.typedef]] to keep inheritance relationships.
   *
   * @param info collected signature info ([[types.Signature]])
   * @param sourceKind how the signature was collected ([[SourceKind]])
   */
  class Node[T](val info: T, val sourceKind: SourceKind) {
    private[this] var _concreteSuper: Node[T] = _
    private[this] var _supers: Seq[Node[T]] = Vector.empty

    private[MixinNodes] def addSuper(node: Node[T]): Unit = _supers :+= node

    private[MixinNodes] def setConcreteSuper(n: Node[T]): Unit = {
      if (_concreteSuper == null) {
        _concreteSuper = n
      }
    }

    private[MixinNodes] def concreteSuper: Option[Node[T]] = Option(_concreteSuper)

    /**
     * Whether this node was collected from a supertype or refinement-super pass.
     *
     * This controls whether [[Map.nodesIterator]] exposes the node itself or follows its primary super node.
     */
    def fromSuper: Boolean = sourceKind.fromSuper

    /**
     * Whether this node represents an allowed source-level implementation for overriding-member search.
     *
     * Direct declarations, exports, and satisfied-self-type members are allowed; nominally inherited and
     * refinement-only members are filtered out.
     */
    def isAllowedImplementationSource: Boolean = sourceKind.isAllowedImplementationSource

    def supers: Seq[Node[T]] = _supers
    def primarySuper: Option[Node[T]] = concreteSuper.orElse(supers.headOption)
  }

  trait Map[T <: Signature] extends SignatureSink[T] {
    def allNodes: Iterator[Node[T]] = allNames.iterator().asScala.map(forName).flatMap(_.nodesIterator)

    lazy val implicitNodes: Seq[Node[T]] = {
      val builder = ArraySeq.newBuilder[Node[T]]
      builder.sizeHint(implicitNames.size)
      val iterator = implicitNames.iterator()
      while (iterator.hasNext) {
        val thisMap = forName(iterator.next)
        thisMap.nodesIterator.foreach { node =>
          if (node.info.isImplicit || node.info.isExtensionMethod) {
            builder += node
          }
        }
      }
      builder.result()
    }

    def nodesIterator(
      decodedName:  String,
      isSupers:     Boolean,
      onlyImplicit: Boolean = false
    ): Iterator[Node[T]] = {

      val allIterator =
        if (decodedName != "") forName(decodedName).nodesIterator
        else if (onlyImplicit) implicitNodes.iterator
        else                   allNodes

      if (isSupers) allIterator.flatMap(node => if (node.fromSuper) Iterator(node) else node.primarySuper.iterator)
      else          allIterator
    }

    def allSignatures: Iterator[T]       = allNodes.map(_.info)
    def intersect(other: Map[T]): Map[T] = new IntersectionMap(this,  other)

    protected val forNameCache = new ConcurrentHashMap[String, AllNodes[T]]()

    def forName(name: String): AllNodes[T]
    def allNames: util.HashSet[String]
    def implicitNames: SmartHashSet[String]
  }

  /**
   * Mutable [[Map]] implementation used while a [[MixinNodes]] builder collects signatures.
   *
   * Signature processors add members through [[SignatureSink.put]] while the map tracks the current [[SourceKind]].
   * Supertype and direct-signature buckets are merged by [[forName]] into the inheritance graph, preserving the
   * signature origin on each [[Node]].
   * Callers should finish the collection with [[sigsFinished]] so completed name lookups can be cached.
   */
  class MapImpl[T <: Signature](private val currentClass: Option[ScTemplateDefinition]) extends Map[T] {
    override val allNames: util.HashSet[String] = new util.HashSet[String]
    override val implicitNames: SmartHashSet[String] = new SmartHashSet[String]

    private case class StoredSignature(signature: T, sourceKind: SourceKind)

    private val thisSignaturesByName: JMap[String, JList[StoredSignature]] = new JMap()
    private val supersSignaturesByName: JMap[String, JList[StoredSignature]] = new JMap()

    // The fields below are mutable because one map is populated across source phases;
    // they track the current phase, declared owner, completion state, and cache state.
    private var currentSourceKind: SourceKind = SourceKind.NominalSuper
    private var declaredOwnerClass: Option[ScTemplateDefinition] = None
    private var finishedBuildingSignatures: Boolean = false

    private val selfTypeOwnerMatchCache = mutable.HashMap.empty[PsiClass, Boolean]

    // Declared source is used after super signatures are collected.
    // Optional owner class lets us classify export-origin signatures precisely for the current class only.
    def supersFinished(ownerClass: Option[ScTemplateDefinition] = None): Unit =
      useDeclaredSource(ownerClass)

    def useSource(sourceKind: SourceKind): Unit = {
      currentSourceKind = sourceKind
      if (sourceKind != SourceKind.Declared) {
        declaredOwnerClass = None
      }
    }

    def useDeclaredSource(ownerClass: Option[ScTemplateDefinition] = None): Unit = {
      currentSourceKind = SourceKind.Declared
      declaredOwnerClass = ownerClass
    }

    def sigsFinished(): Unit = finishedBuildingSignatures = true

    private def classifySource(signature: T): SourceKind =
      currentSourceKind match {
        // Self-typed templates mixed into this class are semantically implementation sources for this inheritor.
        // Classify them during signature collection, so searchers can avoid per-inheritor reverse index lookups.
        case SourceKind.NominalSuper if isSatisfiedSelfTypeSuper(signature) =>
          SourceKind.SelfTypeSuper
        // Exported signatures should be treated as source-level implementation members when their export clause
        // belongs to the current class, not to an ancestor export.
        case SourceKind.Declared if declaredOwnerClass.exists { owner =>
          signature.exportedInCls.exists(ScEquivalenceUtil.areClassesEquivalent(_, owner))
        } =>
          SourceKind.Exported
        case other =>
          other
      }

    private def isSatisfiedSelfTypeSuper(signature: T): Boolean =
      currentClass.exists(isSatisfiedSelfTypeSuper(signature, _))

    /**
     * Checks whether `signature` belongs to a template whose self-type is satisfied by `inheritor`.
     *
     * Signatures from nominal supers are normally classified as [[SourceKind.NominalSuper]].
     * However, a template can contribute an implementation through a self-type without being a nominal parent of the inheritor.
     * This method:
     *  1. finds the signature owner
     *  1. recursively checks all components of its self-type against `inheritor`,
     *  1. and lets [[classifySource]] reclassify a match as [[SourceKind.SelfTypeSuper]].
     *
     * That distinction is needed by implementation search: self-type implementations are valid targets,
     * while ordinary inherited and Scala 2 mixed-in members copied into the inheritor must remain filtered out.
     *
     * Results are cached per owner because many signatures can come from the same self-typed template.
     */
    private def isSatisfiedSelfTypeSuper(signature: T, currentClass: ScTemplateDefinition): Boolean = {
      def selfTypeIsSatisfiedByCurrentClass(tp: ScType): Boolean = tp match {
        case compound: ScCompoundType =>
          compound.components.forall(selfTypeIsSatisfiedByCurrentClass)
        case _ =>
          tp.extractClass.exists { clazz =>
            val areEquivalent = ScEquivalenceUtil.areClassesEquivalent(clazz, currentClass)
            areEquivalent || currentClass.isInheritor(clazz, true)
          }
      }

      val containingClass = signatureContainingClass(signature)
      containingClass.exists { ownerClass =>
        selfTypeOwnerMatchCache.getOrElseUpdate(ownerClass, ownerClass match {
          case ownerTemplate: ScTemplateDefinition if ownerTemplate.selfTypeElement.nonEmpty =>
            ownerTemplate.selfType.exists(selfTypeIsSatisfiedByCurrentClass)
          case _ =>
            false
        })
      }
    }

    private def signatureContainingClass(signature: T): Option[PsiClass] = {
      val namedElement = signature.namedElement

      // There is no single containing-class API for all signatures: namedElement is deliberately only a
      // PsiNamedElement and may be a Java member, a Scala member, or a named PSI element nested in another node.
      // A PsiMethod's direct owner is the most reliable source for Java methods and Scala light methods.
      val methodContainingClass = namedElement match {
        case method: PsiMethod => Option(method.containingClass)
        case _                 => None
      }

      val ownerClassOpt = methodContainingClass
        .orElse {
          // For a Scala source member, `nameContext` points to the ScMember that owns the declaration,
          // which is not necessarily exposed through the PsiMethod API. For example, for `def test` in
          // `trait Impl { this: Trait => def test(): Unit = () }`, `nameContext` identifies the `ScFunction`
          // declared in `Impl`, and this call returns `Some(Impl)`. The result is then used to inspect
          // `Impl.selfType` and decide whether its implementation is applicable to the current class.
          namedElement.containingClassOfNameContext
        }
        .orElse {
          // nameContext is not always a PsiMember (for example, a named element can be nested in a refinement or another PSI wrapper).
          // In those cases the enclosing PsiClass is available only through the parent chain.
          Option(PsiTreeUtil.getParentOfType(namedElement, classOf[PsiClass], false))
        }

      ownerClassOpt
    }

    override def put(signature: T): Unit = {
      val name = signature.name
      val sourceKind = classifySource(signature)
      val buffer =
        if (sourceKind.fromSuper) supersSignaturesByName.computeIfAbsent(name, _ => new SmartList[StoredSignature])
        else                      thisSignaturesByName.computeIfAbsent(name, _ => new SmartList[StoredSignature])

      buffer.add(StoredSignature(signature, sourceKind))

      allNames.add(name)

      if (signature.isImplicit || signature.isExtensionMethod)
        implicitNames.add(name)
    }

    override def forName(name: String): AllNodes[T] = {
      val cleanName = ScalaNamesUtil.clean(name)
      def calculate: AllNodes[T] = {
        val thisSignatures  = thisSignaturesByName.getOrDefault(cleanName, ContainerUtil.emptyList[StoredSignature])
        val superSignatures = supersSignaturesByName.getOrDefault(cleanName, ContainerUtil.emptyList[StoredSignature])
        merge(thisSignatures, superSignatures)
      }

      if (finishedBuildingSignatures) {
        //do not cache intermediate results, forName may be called from resolve
        //for exports, while signatures are still being built
        forNameCache.atomicGetOrElseUpdate(cleanName, calculate)
      } else calculate
    }

    private def merge(thisSignatures: JList[StoredSignature], superSignatures: JList[StoredSignature]): AllNodes[T] = {
      val nodesMap = NodesMap.empty[T]
      val privates = PrivateNodes.empty[T]

      thisSignatures.forEach { storedSig =>
        val thisSig = storedSig.signature

        val node = new Node(thisSig, storedSig.sourceKind)

        if (thisSig.isPrivate) {
          privates.add(node)
        }
        else {
          nodesMap.putIfAbsent(thisSig, node) match {
            case null => // all as expected, unique signature inserted
            case old =>
              if (thisSig.isSynthetic && !old.info.isAbstract) {
                // reinsert real node back instead of synthetic
                nodesMap.put(thisSig, old)
              }
          }
        }
      }

      superSignatures.forEach { storedSig =>
        val superSig = storedSig.signature
        val superNode = new Node(superSig, storedSig.sourceKind)
        if (superSig.isPrivate) {
          privates.add(superNode)
        }
        else {
          nodesMap.putIfAbsent(superSig, superNode) match {
            case null => // not seen before
            case old if !superNode.info.isAbstract && (old.info.isSynthetic || old.info.isAbstract) =>
              //force update thisMap with a non-abstract and non-synthetic node
              nodesMap.put(superSig, superNode)

              //and copy already collected nodes to it
              old.supers.foreach(superNode.addSuper)
              old.concreteSuper.foreach(superNode.setConcreteSuper)

            case old =>
              old.addSuper(superNode)
              if (!superNode.info.isAbstract) {
                old.setConcreteSuper(superNode)
              }
          }
        }
      }

      new AllNodes(Object2ObjectMaps.synchronize(nodesMap), privates)
    }
  }

  class IntersectionMap[T <: Signature](lhsMap: Map[T], rhsMap: Map[T]) extends Map[T] {
    override val allNames: util.HashSet[String] =
      new util.HashSet[String]() {
        addAll(lhsMap.allNames)
        addAll(rhsMap.allNames)
      }

    override val implicitNames: SmartHashSet[String] = {
      val names = new SmartHashSet[String]()
      names.addAll(lhsMap.implicitNames)
      names.addAll(rhsMap.implicitNames)
      names
    }

    override def forName(name: String): AllNodes[T] = {
      val cleanName = ScalaNamesUtil.clean(name)
      val fromLhs   = lhsMap.forName(name)
      val fromRhs   = rhsMap.forName(name)
      forNameCache.atomicGetOrElseUpdate(cleanName, fromLhs.merge(fromRhs))
    }

    override def put(signature: T): Unit = ()
  }

  def emptyMap[T <: Signature]: MixinNodes.Map[T] = new MixinNodes.MapImpl[T](None)

  class AllNodes[T <: Signature](private val publics: NodesMap[T], private val privates: PrivateNodes[T]) {

    def merge(other: AllNodes[T]): AllNodes[T] = {
      val newPublics  = NodesMap.empty[T]
      val newPrivates = PrivateNodes.empty[T]

      def returnType(e: PsiNamedElement): ScType = e match {
        case fn: ScFunction         => fn.returnType.getOrAny
        case m: PsiMethod           => m.getReturnType.toScType()(e.projectContext)
        case tpd: ScTypedDefinition => tpd.`type`().getOrAny
        case other                  => throw new IllegalArgumentException(s"Unexpected signature element of class ${other.getClass}")
      }

      def addNode(node: Node[T]): Unit = {
        node.info match {
          case sig if sig.isPrivate => newPrivates.add(node)
          case _: TypeSignature     => newPublics.put(node.info, node) // todo: merge alias bounds
          case sig: TermSignature   =>
            newPublics.merge(node.info, node, (oldNode, _) => {
              val oldSig     = oldNode.info.asInstanceOf[TermSignature]
              val oldElement = oldSig.namedElement

              val oldOwner = oldElement.containingClassOfNameContext
              val newOwner = sig.namedElement.containingClassOfNameContext

              //1  — old wins
              //-1 — new wins
              val signatureRelativeWeight =
                (for {
                  oldCls <- oldOwner
                  newCls <- newOwner
                } yield
                  if (ScalaPsiUtil.isInheritorDeep(oldCls, newCls))       1
                  else if (ScalaPsiUtil.isInheritorDeep(newCls, oldCls)) -1
                  else                                                    0).getOrElse(0)

              oldElement match {
                case e @ (_: PsiMethod | _: ScBindingPattern | _: ScFieldId) =>
                  if (signatureRelativeWeight == 1)       oldNode
                  else if (signatureRelativeWeight == -1) node
                  else {
                    //None of the signatures win based on linearizaton order, proceed with merging.
                    val sigReturnType = sig.intersectedReturnType.getOrElse(returnType(sig.namedElement))
                    val oldReturnType = oldSig.intersectedReturnType.getOrElse(returnType(e))

                    val combinedSubst         = oldSig.substitutor.followed(sig.substitutor)
                    val intersectedReturnType = ScAndType(oldReturnType, sigReturnType)

                    val intersectedSig =
                      sig.copy(substitutor = combinedSubst, intersectedReturnType = intersectedReturnType.toOption)

                    // Intersected signatures are inherited combinations; they remain "super" entries.
                    // Preserve the self-type signature origin so navigation can treat self-type-derived implementations as valid.
                    val mergedSourceKind = SourceKind.mergedSuperKind(oldNode.sourceKind, node.sourceKind)
                    new Node(intersectedSig, mergedSourceKind).asInstanceOf[Node[T]]
                  }
                case _ => oldNode
              }
            })
        }
      }

      nodesIterator.foreach(addNode)
      other.nodesIterator.foreach(addNode)

      new AllNodes(Object2ObjectMaps.synchronize(newPublics), newPrivates)
    }

    def get(s: T): Option[Node[T]] = {
      publics.get(s) match {
        case null => privates.get(s)
        case node => Some(node)
      }
    }

    def nodesIterator: Iterator[Node[T]] = new Iterator[Node[T]] {
      private val iter1 = publics.values.iterator
      private val iter2 = privates.nodesIterator

      override def hasNext: Boolean = iter1.hasNext || iter2.hasNext

      override def next(): Node[T] = if (iter1.hasNext) iter1.next() else iter2.next()
    }

    def iterator: Iterator[T] = nodesIterator.map(_.info)

    def findNode(named: PsiNamedElement): Option[Node[T]] = {
      var publicNode: Node[T] = null
      publics.forEach { (k, v) =>
        if (publicNode == null && named == k.namedElement) {
          publicNode = v
        }
      }

      Option(publicNode).orElse {
        privates.asScala.find(node => node.info.namedElement == named)
      }
    }

    def isEmpty: Boolean = publics.isEmpty && privates.isEmpty
  }

  //each set contains private members of some class with a fixed name
  //most of them are of size 0 and 1
  type PrivateNodes[T <: Signature] = SmartList[Node[T]]

  object PrivateNodes {
    def empty[T <: Signature]: PrivateNodes[T] = new SmartList[Node[T]]
  }

  implicit class PrivateNodesOps[T <: Signature](list: PrivateNodes[T]) {
    def get(s: T): Option[Node[T]] = {
      val iterator = list.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (s.namedElement == next.info.namedElement) return Some(next)
      }
      None
    }

    def nodesIterator: Iterator[Node[T]] = list.iterator.asScala
  }

  type NodesMap[T <: Signature] = Object2ObjectMap[T, Node[T]]

  object NodesMap {
    private def hashingStrategy[T <: Signature]: Hash.Strategy[T] =
      new Hash.Strategy[T] {
        override def hashCode(t: T): Int = t.equivHashCode
        override def equals(t: T, t1: T): Boolean = {
          if (t == null || t1 == null) false
          else t.equiv(t1)
        }
      }

    def empty[T <: Signature]: NodesMap[T] = new Object2ObjectOpenCustomHashMap[T, Node[T]](2, hashingStrategy[T])
  }

  def linearization(clazz: PsiClass): Seq[ScType] =
    cachedWithRecursionGuard("linearization", clazz, Seq.empty[ScType], ModTracker.libraryAware(clazz)) {
      implicit val context: Context = Context(clazz)

      clazz match {
        case obj: ScObject if obj.isPackageObject && obj.qualifiedName == "scala" =>
          Seq(ScalaType.designator(obj))
        case newTd: ScNewTemplateDefinition =>
          generalLinearization(None, newTd.superTypes)
        case _ =>
          ProgressManager.checkCanceled()
          def default =
            if (clazz.getTypeParameters.isEmpty)
              ScalaType.designator(clazz)
            else
              ScParameterizedType(ScalaType.designator(clazz), clazz.getTypeParameters.map(TypeParameterType(_)).toSeq)

          val classType = clazz match {
            case td: ScTypeDefinition => td.`type`().getOrElse(default)
            case _                    => default
          }
          val supers: Seq[ScType] = {
            implicit val ctx: ProjectContext = clazz
            clazz match {
              case td: ScTemplateDefinition => td.superTypes
              case clazz: PsiClass => clazz.getSuperTypes.map {
                case ctp: PsiClassType =>
                  //noinspection ScalaRedundantCast
                  val cl = ctp.resolve().asInstanceOf[PsiClass]
                  if (cl != null && cl.qualifiedName == "java.lang.Object") ScDesignatorType(cl)
                  else ctp.toScType()
                case ctp => ctp.toScType()
              }.toSeq
            }
          }

          generalLinearization(Some(classType), supers)
      }
    }

  def linearization(compound: ScCompoundType, addTp: Boolean = false)(implicit context: Context): Seq[ScType] = {
    val comps     = compound.components
    val classType = Option.when(addTp)(compound)
    generalLinearization(classType, comps)
  }

  private def generalLinearization(classType: Option[ScType], supers: Iterable[ScType])(implicit context: Context): Seq[ScType] = {
    val baseTypes      = mutable.ArrayBuffer.empty[ScType]
    val qualifiedNames = mutable.HashSet.empty[String]

    def classString(clazz: PsiClass): String =
      clazz match {
        case obj: ScObject => "Object: " + obj.qualifiedName
        case tra: ScTrait  => "Trait: " + tra.qualifiedName
        case _             => "Class: " + clazz.qualifiedName
      }

    def add(tp: ScType): Unit = {
      extractClassOrUpperBoundClass(tp) match {
        case Some((clazz, _)) if clazz.qualifiedName != null && !qualifiedNames.contains(classString(clazz)) =>
          tp +=: baseTypes
          qualifiedNames += classString(clazz)
        case Some((clazz, _)) if clazz.getTypeParameters.nonEmpty =>
          val idx = baseTypes.indexWhere {
            case ExtractClass(cls) if ScEquivalenceUtil.areClassesEquivalent(cls, clazz) => true
            case _                                                                       => false
          }

          if (idx != -1) {
            val newTp = baseTypes(idx)
            if (tp.conforms(newTp)) baseTypes.update(idx, tp)
          }
        case _ =>
          dealias(tp) match {
            case c: ScCompoundType => c +=: baseTypes
            case _                 =>
          }
      }
    }

    val iterator = supers.iterator
    while (iterator.hasNext) {
      var tp = iterator.next()

      @tailrec
      def updateTp(tp: ScType, depth: Int = 0): ScType = {
        tp match {
          case AliasType(_, _, Right(upper), _) =>
            if (tp != upper && depth < 100) updateTp(upper, depth + 1)
            else                            tp
          case _ =>
            tp match {
              case ex: ScExistentialType  => ex.quantified
              case tpt: TypeParameterType => tpt.upperType
              case _                      => tp
            }
        }
      }

      tp = updateTp(tp)
      extractClassOrUpperBoundClass(tp) match {
        case Some((clazz, subst)) =>
          val lin         = linearization(clazz)
          val newIterator = lin.reverseIterator

          while (newIterator.hasNext) {
            val tp = newIterator.next()
            add(subst(tp))
          }
        case _ =>
          dealias(tp) match {
            case c: ScCompoundType =>
              val lin         = linearization(c, addTp = true)
              val newIterator = lin.reverseIterator

              while (newIterator.hasNext) {
                val tp = newIterator.next()
                add(tp)
              }

            case _ =>
          }
      }
    }
    classType.foreach(add)
    baseTypes.to(ArraySeq)
  }

  private def dealias(tp: ScType) = tp match {
    case AliasType(_: ScTypeAliasDefinition, lower, _, effectivelyOpaque) if !effectivelyOpaque => lower.getOrElse(tp)
    case _                                             => tp
  }

  private def extractClassOrUpperBoundClass(tp: ScType): Option[(PsiClass, ScSubstitutor)] =
    tp match {
      case TypeParameterType(tparam) => tparam.upperType.extractClassType
      case ParameterizedType(TypeParameterType(tparam), targs) =>
        val upperBound = tparam.upperType
        upperBound match {
          case ScTypePolymorphicType(internal, tps) =>
            val subst = ScSubstitutor.bind(tps, targs)
            subst(internal).extractClassType
          case t => ParameterizedType(t, targs).extractClassType
        }
      case _ => tp.extractClassType
    }
}
