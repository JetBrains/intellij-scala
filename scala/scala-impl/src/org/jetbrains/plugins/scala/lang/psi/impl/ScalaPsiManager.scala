package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.{JavaPsiFacadeImpl, PsiModificationTrackerImpl}
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.{CalledInAwt, TestOnly}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, CachesUtil, CleanupScheduler, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.idToName
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TermNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.{StableNodes, TermNodes, TypeNodes}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, CachedWithoutModificationCount, ValueWrapper}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.tailrec
import scala.collection.{Seq, mutable}

class ScalaPsiManager(implicit val project: Project) {

  private val inJavaPsiFacade: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  def isInJavaPsiFacade: Boolean = inJavaPsiFacade.get

  private val clearCacheOnChange = new CleanupScheduler
  private val clearCacheOnTopLevelChange = new CleanupScheduler
  private val clearCacheOnRootsChange = new CleanupScheduler

  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]] =
    new ConcurrentHashMap[(ScType, GlobalSearchScope), Seq[ScType]]()

  val implicitCollectorCache: ImplicitCollectorCache =
    CacheTracker.alwaysTrack("ScalaPsiManager.implicitCollectorCache", "ScalaPsiManager.implicitCollectorCache") {
      new ImplicitCollectorCache(project)
    }

  private def dontCacheCompound = ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    if (dontCacheCompound) StableNodes.build(tp, compoundTypeThisType)
    else getStableSignaturesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(valueWrapper = ValueWrapper.SofterReference, clearCacheOnChange)
  private def getStableSignaturesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    StableNodes.build(tp, compoundTypeThisType)
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    if (dontCacheCompound) TypeNodes.build(tp, compoundTypeThisType)
    else getTypesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnChange)
  private def getTypesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    TypeNodes.build(tp, compoundTypeThisType)
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    if (dontCacheCompound) return TermNodes.build(tp, compoundTypeThisType)
    getSignaturesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnChange)
  private def getSignaturesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    TermNodes.build(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnChange)
  def simpleAliasProjectionCached(projection: ScProjectionType): ScType = {
    ScProjectionType.simpleAliasProjection(projection)
  }

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = {
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getPackageImplicitObjectsCached(fqn, scope).toSeq
  }

  import ScalaIndexKeys._

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  private def getPackageImplicitObjectsCached(fqn: String, scope: GlobalSearchScope): Iterable[ScObject] =
    IMPLICIT_OBJECT_KEY.elements(cleanFqn(fqn), scope, classOf[ScObject])

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def getCachedPackage(inFqn: String): Option[PsiPackage] = {
    //to find java packages with scala keyword name as PsiPackage not ScSyntheticPackage
    val fqn = cleanFqn(inFqn)
    Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
  }

  private[this] def isPackageOutOfScope(`package`: PsiPackage)
                                       (implicit scope: GlobalSearchScope): Boolean =
    `package`.getSubPackages(scope).isEmpty &&
      `package`.getClasses(scope).isEmpty

  private[this] def isScalaPackageInScope(fqn: String)
                                         (implicit scope: GlobalSearchScope): Boolean =
    PACKAGE_FQN_KEY.hasIntegerElements(fqn, scope, classOf[ScPackaging]) ||
      PACKAGE_OBJECT_KEY.hasIntegerElements(fqn, scope, classOf[PsiClass])

  def getCachedPackageInScope(fqn: String)
                             (implicit scope: GlobalSearchScope): Option[PsiPackage] =
    if (DumbService.getInstance(project).isDumb)
      None
    else
      getCachedPackage(fqn).filter { `package` =>
        isScalaPackageInScope(fqn) ||
          !isPackageOutOfScope(`package`)
      }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] = {
    def getCachedFacadeClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] = {
      inJavaPsiFacade.set(true)
      try {
        val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
        if (clazz == null || clazz.isInstanceOf[ScTemplateDefinition] || clazz.isInstanceOf[PsiClassWrapper]) None
        else Option(clazz)
      } finally {
        inJavaPsiFacade.set(false)
      }
    }

    val res = ScalaShortNamesCacheManager.getInstance(project).getClassByFQName(fqn, scope)
    Option(res).orElse(getCachedFacadeClass(scope, fqn))
  }

  def getStableAliasesByName(name: String, scope: GlobalSearchScope): Iterable[ScTypeAlias] =
    TYPE_ALIAS_NAME_KEY.elements(cleanFqn(name), scope, classOf[ScTypeAlias])

  def getStableAliasesByFqn(fqn: String, scope: GlobalSearchScope): Iterable[ScTypeAlias] =
    STABLE_ALIAS_FQN_KEY
      .integerElements(fqn, scope, classOf[ScTypeAlias])
      .filter(_.qualifiedNameOpt.contains(fqn))

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass] = {
    val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(name, scope)
    val buffer: mutable.Buffer[PsiClass] = PsiShortNamesCache.getInstance(project).getClassesByName(name, scope).filterNot(p =>
      p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
    ).toBuffer
    val classesIterator = scalaClasses.iterator
    while (classesIterator.hasNext) {
      val clazz = classesIterator.next()
      buffer += clazz
    }
    buffer
  }

  def getClasses(`package`: PsiPackage)
                (implicit scope: GlobalSearchScope): Array[PsiClass] =
    if (DumbService.getInstance(project).isDumb)
      PsiClass.EMPTY_ARRAY
    else
      `package`.getQualifiedName match {
        case ScalaLowerCase => getScalaPackageClassesCached
        case qualifiedName => getJavaClasses(`package`) ++ getScalaClasses(qualifiedName)
      }

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  private[this] def getScalaPackageClassesCached(implicit scope: GlobalSearchScope): Array[PsiClass] =
    getScalaClassNamesCached(ScalaLowerCase).flatMap { className =>
      getCachedClasses(scope, ScalaLowerCase + "." + className)
    }.toArray

  private[this] def getJavaClasses(`package`: PsiPackage)
                                  (implicit scope: GlobalSearchScope) = {
    inJavaPsiFacade.set(true)
    try {
      JavaPsiFacade.getInstance(project)
        .asInstanceOf[JavaPsiFacadeImpl]
        .getClasses(`package`, scope)
        .filter {
          case _: ScTemplateDefinition |
               _: PsiClassWrapper => false
          case _ => true
        }
    } finally {
      inJavaPsiFacade.set(false)
    }
  }

  private[this] def getScalaClasses(qualifiedName: String)
                                   (implicit scope: GlobalSearchScope) = {
    val scalaQualifiedName = cleanFqn(qualifiedName)
    val toFqn = if (scalaQualifiedName.isEmpty)
      identity(_: String)
    else
      scalaQualifiedName + "." + (_: String)

    getScalaClassNamesCached(scalaQualifiedName)
      .map(toFqn)
      .flatMap(getCachedClasses(scope, _))
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
    def getCachedFacadeClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
      inJavaPsiFacade.set(true)
      try {
        val classes = JavaPsiFacade.getInstance(project).findClasses(fqn, new DelegatingGlobalSearchScope(scope) {
          override def compare(file1: VirtualFile, file2: VirtualFile): Int = 0
        }).filterNot { p =>
          p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
        }

        classes ++ SyntheticClassProducer.getAllClasses(fqn, scope)
      } finally {
        inJavaPsiFacade.set(false)
      }
    }

    if (DumbService.getInstance(project).isDumb) return Array.empty

    val classes = getCachedFacadeClasses(scope, cleanFqn(fqn))
    val fromScala = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(fqn, scope)
    val synthetic = SyntheticClassProducer.getAllClasses(fqn, scope)
    Array.concat(classes, fromScala.toArray, synthetic)
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] =
    elementScope.function1Type()

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias] =
    getStableAliasesByFqn("scala.Seq", scope).headOption

  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getJavaPackageClassNamesCached(psiPackage, scope)
  }

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  private def getJavaPackageClassNamesCached(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    val key = cleanFqn(psiPackage.getQualifiedName)
    val classes = JAVA_CLASS_NAME_IN_PACKAGE_KEY.elements(key, scope, classOf[PsiClass]).toSet

    val additionalClasses = classes.flatMap {
      case definition: ScTypeDefinition => definition.additionalClassJavaName
      case _ => None
    }

    classes.map(_.getName) ++ additionalClasses
  }

  def getScalaPackageClassNames(implicit scope: GlobalSearchScope): Set[String] =
    if (DumbService.getInstance(project).isDumb) Set.empty
    else getScalaClassNamesCached(ScalaLowerCase)

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  private[this] def getScalaClassNamesCached(scalaQualifiedName: String)
                                            (implicit scope: GlobalSearchScope): Set[String] =
    CLASS_NAME_IN_PACKAGE_KEY.elements(
      scalaQualifiedName,
      scope,
      classOf[PsiClass]
    ).map(_.name).toSet

  private def clearCaches(): Unit = {
    new ProjectContext(project).typeSystem.clearCache()
    ParameterizedType.substitutorCache.clear()
    PropertyMethods.clearCache()
    collectImplicitObjectsCache.clear()
    implicitCollectorCache.clear()
    idToName.clear()
  }

  private def clearOnChange(): Unit = {
    clearCacheOnChange.fireCleanup()
    clearCaches()
  }

  private def clearOnLowMemory(): Unit = clearAllCaches()

  private def clearOnTopLevelChange(): Unit = {
    clearOnChange()
    clearCacheOnTopLevelChange.fireCleanup()
    syntheticPackages.clear()
  }

  private def clearOnRootsChange(): Unit = {
    clearOnTopLevelChange()
    clearCacheOnRootsChange.fireCleanup()
  }

  private val psiChangeListener = ScalaPsiChangeListener(clearOnScalaElementChange, () => clearOnNonScalaChange())

  private[impl] def projectOpened(): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      LOG.debug("Clear caches on root change")
      clearOnRootsChange()
    }
    registerLowMemoryWatcher(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiChangeListener, project.unloadAwareDisposable)
  }

  private val syntheticPackages = ContainerUtil.createConcurrentWeakValueMap[String, AnyRef]()
  private val emptyMarker: AnyRef = ObjectUtils.sentinel("syntheticPackageEmptyMarker")

  def syntheticPackage(fqn: String): ScSyntheticPackage = {
    val syntheticOrEmptyMarker =
      syntheticPackages.computeIfAbsent(fqn, fqn => Option(ScSyntheticPackage(fqn)(project)).getOrElse(emptyMarker))

    syntheticOrEmptyMarker match {
      case s: ScSyntheticPackage => s
      case _ => null
    }
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnChange)
  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val types = typeParameter.getExtendsListTypes ++ typeParameter.getImplementsListTypes
    if (types.isEmpty) Any
    else andType(types)
  }

  private def andType(psiTypes: Seq[PsiType]): ScType = {
    new ProjectContext(project).typeSystem.andType(psiTypes.map(_.toScType()))
  }

  def getStableTypeAliasesNames: Iterable[String] = {
    import ScalaIndexKeys._
    STABLE_ALIAS_NAME_KEY.allKeys
  }

  val TopLevelModificationTracker: SimpleModificationTracker = new SimpleModificationTracker {
    private val psiModTracker =
      PsiManager.getInstance(project).getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]

    override def incModificationCount(): Unit = {
      psiModTracker.incCounter() //update javaStructureModCount on top-level scala change
      clearOnTopLevelChange()
      super.incModificationCount()
    }
  }

  private def clearOnScalaElementChange(psiElement: PsiElement): Unit = {
    clearOnChange()
    updateScalaModificationCount(psiElement)
  }

  private def clearOnNonScalaChange(): Unit = {
    clearOnChange()
    TopLevelModificationTracker.incModificationCount()
  }

  @tailrec
  private def updateScalaModificationCount(element: PsiElement): Unit = element match {
    case null => TopLevelModificationTracker.incModificationCount()
    case owner: ScExpression if BlockModificationTracker.hasStableType(owner) =>
      BlockModificationTracker.incrementLocalCounter(owner)
    case _ => updateScalaModificationCount(element.getContext)
  }

  val rootManager: ModificationTracker = ProjectRootManager.getInstance(project)

  sealed abstract class SignatureCaches[T <: Signature](val nodes: MixinNodes[T]) {

    private val forLibraryMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map]  = ContainerUtil.createConcurrentWeakMap()
    private val forTopLevelMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map] = ContainerUtil.createConcurrentWeakMap()

    clearCacheOnRootsChange.subscribe(() => forLibraryMap.clear())
    clearCacheOnTopLevelChange.subscribe(() => forTopLevelMap.clear())

    private def forLibraryClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forLibraryMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    private def forTopLevelClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forTopLevelMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    def cachedMap(clazz: PsiClass, withSupers: Boolean): nodes.Map = {
      CachesUtil.libraryAwareModTracker(clazz) match {
        case `rootManager`               => forLibraryClasses(clazz, withSupers)
        case TopLevelModificationTracker => forTopLevelClasses(clazz, withSupers)
        case tracker =>

          @CachedInUserData(clazz, tracker)
          def cachedInUserData(clazz: PsiClass, n: MixinNodes[T]): nodes.Map = nodes.build(clazz, withSupers)

          //@CachedInUserData creates a single map for all 3 cases, so we need to pass `nodes` as a parameter to have different keys
          cachedInUserData(clazz, nodes)
      }
    }
  }

  object TermNodesCache   extends SignatureCaches(TermNodes)
  object StableNodesCache extends SignatureCaches(StableNodes)
  object TypeNodesCache   extends SignatureCaches(TypeNodes)

  def clearAllCaches(): Unit = invokeLater {
    doClearAllCaches()
  }

  def clearAllCachesAndWait(): Unit = invokeAndWait {
    doClearAllCaches()
  }

  @CalledInAwt()
  private def doClearAllCaches(): Unit = {
    if (!project.isDisposed) {
      clearOnRootsChange()
      TopLevelModificationTracker.incModificationCount()
    }
  }

  @TestOnly
  def clearCachesOnChange(): Unit = {
    clearOnChange()
  }
}

object ScalaPsiManager {
  val TYPE_VARIABLE_KEY: Key[TypeParameterType] = Key.create("type.variable.key")

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager")

  def instance(implicit ctx: ProjectContext): ScalaPsiManager =
    ctx.getService(classOf[ScalaPsiManagerHolder]).get

  private def registerLowMemoryWatcher(project: Project): Unit = {
    LowMemoryWatcher.register(() => {
      LOG.debug("Clear caches on low memory")
      val manager = ScalaPsiManager.instance(project)
      manager.clearOnLowMemory()
    }, project)
  }

  object AnyScalaPsiModificationTracker extends SimpleModificationTracker

  implicit val ImplicitCollectorCacheCapabilities: CacheCapabilities[ImplicitCollectorCache] =
    new CacheCapabilities[ImplicitCollectorCache] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache: CacheType): Unit = cache.clear()
    }
}

private class ScalaPsiManagerHolder(implicit project: Project) {
  private val scalaPsiManager: AtomicReference[ScalaPsiManager] = new AtomicReference()

  def get: ScalaPsiManager = {
    if (scalaPsiManager.get() == null && !project.isDisposed) {
      scalaPsiManager.compareAndSet(null, new ScalaPsiManager)
    }
    scalaPsiManager.get()
  }

  def dispose(): Unit =
    scalaPsiManager.set(null)
}

private class ScalaPsiManagerListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    val holder = managerHolder(project)
    holder.get.projectOpened()
  }

  override def projectClosing(project: Project): Unit = {
    val holder = managerHolder(project)
    holder.get.clearAllCaches()
    holder.dispose()
  }

  private def managerHolder(project: Project): ScalaPsiManagerHolder =
    project.getService(classOf[ScalaPsiManagerHolder])
}