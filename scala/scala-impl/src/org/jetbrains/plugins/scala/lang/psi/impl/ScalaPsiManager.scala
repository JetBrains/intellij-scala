package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util
import java.util.concurrent.ConcurrentMap

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project, ProjectManagerListener, ProjectUtil}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.{JavaPsiFacadeImpl, PsiModificationTrackerImpl, PsiTreeChangeEventImpl}
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.{ArrayUtil, ObjectUtils}
import org.jetbrains.annotations.{CalledInAwt, TestOnly}
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, CachesUtil, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.idToName
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TermNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.{StableNodes, TermNodes, TypeNodes}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, CachedWithoutModificationCount, ValueWrapper}
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectContext, ProjectExt, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.tailrec
import scala.collection.{Seq, mutable}

class ScalaPsiManager(implicit val project: Project) {

  private val inJavaPsiFacade: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  def isInJavaPsiFacade: Boolean = inJavaPsiFacade.get

  private val clearCacheOnChange = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()
  private val clearCacheOnTopLevelChange = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()
  private val clearCacheOnRootsChange = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()

  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]] =
    ContainerUtil.newConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]]()

  val implicitCollectorCache: ImplicitCollectorCache =
    CacheTracker.alwaysTrack("ScalaPsiManager.implicitCollectorCache", "ScalaPsiManager.implicitCollectorCache") {
      new ImplicitCollectorCache(project)
    }

  private def dontCacheCompound = ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnChange)
  def inheritorOrThisObjects(td: ScTemplateDefinition): Set[ScObject] = ScalaInheritors.findInheritorObjects(td)

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
    IMPLICIT_OBJECT_KEY.elements(ScalaNamesUtil.cleanFqn(fqn), scope, classOf[ScObject])

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def getCachedPackage(inFqn: String): Option[PsiPackage] = {
    //to find java packages with scala keyword name as PsiPackage not ScSyntheticPackage
    val fqn = ScalaNamesUtil.cleanFqn(inFqn)
    Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
  }

  private def isPackageInScope(p: PsiPackage, scope: GlobalSearchScope): Boolean = {
    def scalaPackageExistsInScope: Boolean = {
      val fqn = p.getQualifiedName
      PACKAGE_FQN_KEY.hasIntegerElements(fqn, scope, classOf[ScPackaging]) ||
        PACKAGE_OBJECT_KEY.hasIntegerElements(fqn, scope, classOf[PsiClass])
    }
    scalaPackageExistsInScope || p.getSubPackages(scope).nonEmpty || p.getClasses(scope).nonEmpty
  }

  def getCachedPackageInScope(fqn: String, scope: GlobalSearchScope): Option[PsiPackage] =
    getCachedPackage(fqn).filter(isPackageInScope(_, scope))

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
    TYPE_ALIAS_NAME_KEY.elements(ScalaNamesUtil.cleanFqn(name), scope, classOf[ScTypeAlias])

  def getStableAliasByFqn(fqn: String, scope: GlobalSearchScope): Iterable[ScTypeAlias] =
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

  def getClasses(pack: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    if (pack.getQualifiedName == "scala") getClassesCached(pack, scope)
    else getClassesImpl(pack, scope)
  }

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  private def getClassesCached(pack: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = getClassesImpl(pack, scope)

  private[this] def getClassesImpl(pack: PsiPackage, scope: GlobalSearchScope): Array[PsiClass] = {
    val classes = {
      inJavaPsiFacade.set(true)
      try {
        JavaPsiFacade.getInstance(project).asInstanceOf[JavaPsiFacadeImpl].getClasses(pack, scope).filterNot(p =>
          p.isInstanceOf[ScTemplateDefinition] || p.isInstanceOf[PsiClassWrapper]
        )
      } finally {
        inJavaPsiFacade.set(false)
      }
    }
    val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClasses(pack, scope)
    classes ++ scalaClasses
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

        ArrayUtil.mergeArrays(classes, SyntheticClassProducer.getAllClasses(fqn, scope))
      } finally {
        inJavaPsiFacade.set(false)
      }
    }

    if (DumbService.getInstance(project).isDumb) return Array.empty

    val classes = getCachedFacadeClasses(scope, ScalaNamesUtil.cleanFqn(fqn))
    val fromScala = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(fqn, scope)
    ArrayUtil.mergeArrays(classes, ArrayUtil.mergeArrays(fromScala.toArray, SyntheticClassProducer.getAllClasses(fqn, scope)))
  }

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] =
    elementScope.function1Type()

  @CachedWithoutModificationCount(ValueWrapper.SofterReference, clearCacheOnTopLevelChange)
  def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias] =
    getStableAliasByFqn("scala.Seq", scope).headOption

  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getJavaPackageClassNamesCached(psiPackage, scope)
  }

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  private def getJavaPackageClassNamesCached(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    val key = ScalaNamesUtil.cleanFqn(psiPackage.getQualifiedName)
    val classes = JAVA_CLASS_NAME_IN_PACKAGE_KEY.elements(key, scope, classOf[PsiClass]).toSet

    val additionalClasses = classes.flatMap {
      case definition: ScTypeDefinition => definition.additionalClassJavaName
      case _ => None
    }

    classes.map(_.getName) ++ additionalClasses
  }

  def getScalaClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getScalaClassNamesCached(psiPackage, scope)
  }

  @CachedWithoutModificationCount(ValueWrapper.None, clearCacheOnTopLevelChange)
  def getScalaClassNamesCached(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] =
    CLASS_NAME_IN_PACKAGE_KEY.elements(ScalaNamesUtil.cleanFqn(psiPackage.getQualifiedName), scope, classOf[PsiClass])
      .map(_.name)
      .toSet

  private def clearCaches(): Unit = {
    new ProjectContext(project).typeSystem.clearCache()
    ParameterizedType.substitutorCache.clear()
    PropertyMethods.clearCache()
    collectImplicitObjectsCache.clear()
    implicitCollectorCache.clear()
    idToName.clear()
  }

  private def clearOnChange(): Unit = {
    clearCacheOnChange.foreach(_.clear())
    clearCaches()
  }

  private def clearOnLowMemory(): Unit = clearAllCaches()

  private def clearOnTopLevelChange(): Unit = {
    clearOnChange()
    clearCacheOnTopLevelChange.foreach(_.clear())
    syntheticPackages.clear()
  }

  private def clearOnRootsChange(): Unit = {
    clearOnTopLevelChange()
    clearCacheOnRootsChange.foreach(_.clear())
  }

  private[impl] def projectOpened(): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      LOG.debug("Clear caches on root change")
      clearOnRootsChange()
    }
    registerLowMemoryWatcher(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(CacheInvalidator, project)
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

  private val NonScalaModificationTracker = new SimpleModificationTracker

  val TopLevelModificationTracker: SimpleModificationTracker = new SimpleModificationTracker {
    private val psiModTracker =
      PsiManager.getInstance(project).getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]

    override def getModificationCount: Long =
      super.getModificationCount + NonScalaModificationTracker.getModificationCount

    override def incModificationCount(): Unit = {
      psiModTracker.incCounter() //update javaStructureModCount on top-level scala change
      super.incModificationCount()
    }
  }

  val rootManager: ModificationTracker = ProjectRootManager.getInstance(project)

  sealed abstract class SignatureCaches[T <: Signature](val nodes: MixinNodes[T]) {

    private val forLibraryMap: ConcurrentMap[PsiClass, nodes.Map] = ContainerUtil.createConcurrentWeakMap()
    private val forTopLevelMap: ConcurrentMap[PsiClass, nodes.Map] = ContainerUtil.createConcurrentWeakMap()

    clearCacheOnRootsChange += forLibraryMap
    clearCacheOnTopLevelChange += forTopLevelMap

    private def forLibraryClasses(clazz: PsiClass): nodes.Map = forLibraryMap.computeIfAbsent(clazz, nodes.build)

    private def forTopLevelClasses(clazz: PsiClass): nodes.Map = forTopLevelMap.computeIfAbsent(clazz, nodes.build)

    def cachedMap(clazz: PsiClass): nodes.Map = {
      CachesUtil.libraryAwareModTracker(clazz) match {
        case `rootManager`               => forLibraryClasses(clazz)
        case TopLevelModificationTracker => forTopLevelClasses(clazz)
        case tracker =>

          @CachedInUserData(clazz, tracker)
          def cachedInUserData(clazz: PsiClass, n: MixinNodes[T]): nodes.Map = nodes.build(clazz)

          //@CachedInUserData creates a single map for all 3 cases, so we need to pass `nodes` as a parameter to have different keys
          cachedInUserData(clazz, nodes)
      }
    }
  }

  object TermNodesCache   extends SignatureCaches(TermNodes)
  object StableNodesCache extends SignatureCaches(StableNodes)
  object TypeNodesCache   extends SignatureCaches(TypeNodes)

  object CacheInvalidator extends PsiTreeChangeAdapter {
    @volatile
    private var topLevelModCount: Long = 0L

    private def fromIdeaInternalFile(event: PsiTreeChangeEvent) = {
      val virtFile = event.getFile match {
        case null => event.getOldValue.asOptionOf[VirtualFile]
        case file =>
          val fileType = file.getFileType
          if (fileType == ScalaFileType.INSTANCE || fileType == JavaFileType.INSTANCE) None
          else Option(file.getVirtualFile)
      }
      virtFile.exists(ProjectUtil.isProjectOrWorkspaceFile)
    }

    private def shouldClear(event: PsiTreeChangeEvent): Boolean = {
      event match {
        case impl: PsiTreeChangeEventImpl if impl.isGenericChange => false
        case _ if fromIdeaInternalFile(event)                     => false
        case _                                                    => true
      }
    }

    private def onPsiChange(event: PsiTreeChangeEvent, psiElement: PsiElement): Unit = {
      if (!shouldClear(event)) return

      LOG.debug(s"Clear caches on psi change: $event")

      if (psiElement != null && psiElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
        @tailrec
        def updateModificationCount(element: PsiElement): Unit = element match {
          case null => TopLevelModificationTracker.incModificationCount()
          case _: ScalaCodeFragment | _: PsiComment => // do not update on changes in dummy file or comments
          case owner: ScExpression if BlockModificationTracker.hasStableType(owner) =>
            BlockModificationTracker.incrementLocalCounter(owner)
          case _ => updateModificationCount(element.getContext)
        }

        updateModificationCount(psiElement)
      } else {
        NonScalaModificationTracker.incModificationCount()
      }

      TopLevelModificationTracker.getModificationCount match {
        case count if topLevelModCount == count =>
          clearOnChange()
        case count =>
          topLevelModCount = count
          clearOnTopLevelChange()
      }
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = onPsiChange(event, event.getParent)

    override def childReplaced(event: PsiTreeChangeEvent): Unit = {
      val parent = event.getParent

      // Ignore changed literals as long as the type is preserved
      if (!parent.module.exists(_.literalTypesEnabled) && parent.is[ScLiteral] && !parent.is[ScInterpolatedStringLiteral]) {
        return
      }

      val changedElement =
        if (event.getNewChild.getClass == event.getOldChild.getClass)
          event.getNewChild
        else parent

      onPsiChange(event, changedElement)
    }

    override def childAdded(event: PsiTreeChangeEvent): Unit = onPsiChange(event, event.getChild)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = onPsiChange(event, event.getParent)

    override def childMoved(event: PsiTreeChangeEvent): Unit = onPsiChange(event, event.getChild)

    override def propertyChanged(event: PsiTreeChangeEvent): Unit = onPsiChange(event, null)
  }

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

private class ScalaPsiManagerHolder {
  private var scalaPsiManager: ScalaPsiManager = _

  def get: ScalaPsiManager = scalaPsiManager

  def init(implicit project: Project): Unit =
    scalaPsiManager = new ScalaPsiManager

  def dispose(): Unit =
    scalaPsiManager = null
}

private class ScalaPsiManagerListener extends ProjectManagerListener {

  override def projectOpened(project: Project): Unit = {
    val holder = managerHolder(project)
    holder.init(project)
    holder.get.projectOpened()
  }

  override def projectClosed(project: Project): Unit = {
    val holder = managerHolder(project)
    holder.get.clearAllCaches()
    holder.dispose()
  }

  private def managerHolder(project: Project): ScalaPsiManagerHolder =
    project.getService(classOf[ScalaPsiManagerHolder])
}