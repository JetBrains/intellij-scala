package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.util
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong

import com.intellij.ProjectTopics
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project, ProjectUtil}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.util._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.{JavaPsiFacadeImpl, PsiTreeChangeEventImpl}
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.caches.{CachesUtil, ScalaShortNamesCacheManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.idToName
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticPackage
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.ParameterlessNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers._
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithoutModificationCount, ModCount, ValueWrapper}
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.scalafmt.config.ScalafmtConfig
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor.TextRanges

import scala.collection.{Seq, mutable}

class ScalaPsiManager(val project: Project) {

  implicit def projectContext: Project = project

  private val inJavaPsiFacade: ThreadLocal[Boolean] = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  def isInJavaPsiFacade: Boolean = inJavaPsiFacade.get

  private val clearCacheOnChange = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()
  private val clearCacheOnLowMemory = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()
  private val clearCacheOnOutOfBlockChange = new mutable.ArrayBuffer[util.Map[_ <: Any, _ <: Any]]()

  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]] =
    ContainerUtil.newConcurrentMap[(ScType, GlobalSearchScope), Seq[ScType]]()

  val implicitCollectorCache: ImplicitCollectorCache = new ImplicitCollectorCache(project)

  def getScalafmtProjectConfig(vFile: VirtualFile): ScalafmtConfig = ScalaFmtPreFormatProcessor.storeOrUpdate(scalafmtConfig, vFile, project)
  private val scalafmtConfig: ConcurrentMap[VirtualFile, (ScalafmtConfig, Long)] = ContainerUtil.createConcurrentWeakMap[VirtualFile, (ScalafmtConfig, Long)]()

  private def dontCacheCompound = ScalaProjectSettings.getInstance(project).isDontCacheCompoundTypes

  def getParameterlessSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    if (dontCacheCompound) ParameterlessNodes.build(tp, compoundTypeThisType)
    else getParameterlessSignaturesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(synchronized = false, valueWrapper = ValueWrapper.SofterReference, clearCacheOnChange, clearCacheOnLowMemory)
  private def getParameterlessSignaturesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap = {
    ParameterlessNodes.build(tp, compoundTypeThisType)
  }

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    if (dontCacheCompound) TypeNodes.build(tp, compoundTypeThisType)
    else getTypesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnChange, clearCacheOnLowMemory)
  private def getTypesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap = {
    TypeNodes.build(tp, compoundTypeThisType)
  }

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    if (dontCacheCompound) return SignatureNodes.build(tp, compoundTypeThisType)
    getSignaturesCached(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnChange, clearCacheOnLowMemory)
  private def getSignaturesCached(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    SignatureNodes.build(tp, compoundTypeThisType)
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnChange, clearCacheOnLowMemory)
  def simpleAliasProjectionCached(projection: ScProjectionType): ScType = {
    ScProjectionType.simpleAliasProjection(projection)
  }

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] = {
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getPackageImplicitObjectsCached(fqn, scope).toSeq
  }

  import ScalaIndexKeys._

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnOutOfBlockChange)
  private def getPackageImplicitObjectsCached(fqn: String, scope: GlobalSearchScope): Iterable[ScObject] =
    IMPLICIT_OBJECT_KEY.elements(ScalaNamesUtil.cleanFqn(fqn), scope, classOf[ScObject])

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnOutOfBlockChange)
  def getCachedPackage(inFqn: String): Option[PsiPackage] = {
    //to find java packages with scala keyword name as PsiPackage not ScSyntheticPackage
    val fqn = ScalaNamesUtil.cleanFqn(inFqn)
    Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnOutOfBlockChange)
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

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.None, clearCacheOnLowMemory, clearCacheOnOutOfBlockChange)
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

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnOutOfBlockChange)
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

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnOutOfBlockChange)
  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] =
    elementScope.function1Type()

  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getJavaPackageClassNamesCached(psiPackage, scope)
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.None, clearCacheOnLowMemory, clearCacheOnOutOfBlockChange)
  private def getJavaPackageClassNamesCached(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    val classes = JAVA_CLASS_NAME_IN_PACKAGE_KEY.elements(ScalaNamesUtil.cleanFqn(psiPackage.getQualifiedName), scope, classOf[PsiClass])

    def names(clazz: PsiClass): Set[String] = {
      def additionalJavaNames = clazz match {
        case definition: ScTemplateDefinition => definition.additionalJavaNames
        case _ => Array.empty
      }

      Set(clazz.getName) ++ additionalJavaNames
    }

    classes.flatMap(names).toSet
  }

  def getScalaClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getScalaClassNamesCached(psiPackage, scope)
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.None, clearCacheOnLowMemory, clearCacheOnOutOfBlockChange)
  def getScalaClassNamesCached(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] =
    CLASS_NAME_IN_PACKAGE_KEY.elements(ScalaNamesUtil.cleanFqn(psiPackage.getQualifiedName), scope, classOf[PsiClass])
      .map(_.name)
      .toSet

  private def clearCaches(): Unit = {
    val typeSystem = project.typeSystem

    typeSystem.clearCache()
    ParameterizedType.substitutorCache.clear()
    ScParameterizedType.cache.clear()
    collectImplicitObjectsCache.clear()
    implicitCollectorCache.clear()
    idToName.clear()
  }

  private def clearOnChange(): Unit = {
    clearCacheOnChange.foreach(_.clear())
    clearCaches()
  }

  private def clearOnLowMemory(): Unit = {
    clearCacheOnLowMemory.foreach(_.clear())
    clearCaches()
  }

  private def clearOnJavaStructureChange(): Unit = {
    clearCacheOnOutOfBlockChange.foreach(_.clear())
    syntheticPackages.clear()
  }

  private[impl] def projectOpened(): Unit = {
    import ScalaPsiManager._

    subscribeToRootsChange(project)
    registerLowMemoryWatcher(project)
    PsiManager.getInstance(project).addPsiTreeChangeListener(CacheInvalidator, project)
  }

  private val syntheticPackages = ContainerUtil.createWeakValueMap[String, AnyRef]()
  private val emptyMarker: AnyRef = new Object

  def syntheticPackage(fqn: String): ScSyntheticPackage = {
    var p = syntheticPackages.get(fqn)
    if (p == null) {
      p = ScSyntheticPackage(fqn)(project)
      if (p == null) p = emptyMarker
      synchronized {
        val pp = syntheticPackages.get(fqn)
        if (pp == null) {
          syntheticPackages.put(fqn, p)
        } else {
          p = pp
        }
      }
    }

    p match {
      case synth: ScSyntheticPackage => synth
      case _ => null
    }
  }

  @CachedWithoutModificationCount(synchronized = false, ValueWrapper.SofterReference, clearCacheOnChange)
  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val types = typeParameter.getExtendsListTypes ++ typeParameter.getImplementsListTypes
    if (types.isEmpty) Any
    else andType(types)
  }

  private def andType(psiTypes: Seq[PsiType]): ScType = {
    projectContext.typeSystem.andType(psiTypes.map(_.toScType()))
  }

  def getStableTypeAliasesNames: Iterable[String] = {
    import ScalaIndexKeys._
    STABLE_ALIAS_NAME_KEY.allKeys
  }

  object CacheInvalidator extends PsiTreeChangeAdapter {
    @volatile
    private var javaStructureModCount: Long = 0L

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

    private def onPsiChange(event: PsiTreeChangeEvent): Unit = {
      event match {
        case impl: PsiTreeChangeEventImpl if impl.isGenericChange => return
        case _ if fromIdeaInternalFile(event) => return
        case _ =>
      }

      ScalaPsiManager.LOG.debug(s"Clear caches on psi change: $event")

      CachesUtil.updateModificationCount(event.getParent)
      clearOnChange()
      val count = PsiModificationTracker.SERVICE.getInstance(project).getJavaStructureModificationCount
      if (javaStructureModCount != count) {
        javaStructureModCount = count
        clearOnJavaStructureChange()
      }
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = onPsiChange(event)

    override def childReplaced(event: PsiTreeChangeEvent): Unit = onPsiChange(event)

    override def childAdded(event: PsiTreeChangeEvent): Unit = onPsiChange(event)

    override def childrenChanged(event: PsiTreeChangeEvent): Unit = onPsiChange(event)

    override def childMoved(event: PsiTreeChangeEvent): Unit = onPsiChange(event)

    override def propertyChanged(event: PsiTreeChangeEvent): Unit = onPsiChange(event)
  }

  val modificationTracker: ScalaPsiModificationTracker = new ScalaPsiModificationTracker(project)

  def getModificationCount: Long = modificationTracker.getModificationCount

  def incModificationCount(): Long = modificationTracker.incModificationCount()

  def clearAllCaches(): Unit = {
    clearOnChange()
    clearOnJavaStructureChange()
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
    ctx.project.getComponent(classOf[ScalaPsiManagerComponent]).instance

  private def subscribeToRootsChange(project: Project): Unit = {
    project.getMessageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener {
      override def beforeRootsChange(event: ModuleRootEvent) {}

      override def rootsChanged(event: ModuleRootEvent) {
        LOG.debug("Clear caches on root change")
        val manager = ScalaPsiManager.instance(project)
        manager.clearOnChange()
        manager.clearOnJavaStructureChange()
        project.putUserData(CachesUtil.PROJECT_HAS_DOTTY_KEY, null)
      }
    })
  }

  private def registerLowMemoryWatcher(project: Project): Unit = {
    LowMemoryWatcher.register(() => {
      LOG.debug("Clear caches on low memory")
      val manager = ScalaPsiManager.instance(project)
      manager.clearOnLowMemory()
    }, project)
  }

  object AnyScalaPsiModificationTracker extends SimpleModificationTracker
}

class ScalaPsiManagerComponent(project: Project) extends AbstractProjectComponent(project) {
  private var manager = new ScalaPsiManager(project)

  def instance: ScalaPsiManager =
    if (manager != null) manager
    else throw new IllegalStateException("ScalaPsiManager cannot be used after disposing.")

  override def projectOpened(): Unit = {
    manager.projectOpened()
  }

  override def projectClosed(): Unit = {
    manager.clearAllCaches()
  }

  override def disposeComponent(): Unit = {
    manager = null
  }
}

class ScalaPsiModificationTracker(project: Project) extends ModificationTracker {

  private val myRawModificationCount = new AtomicLong(0)

  private val mainModificationTracker = PsiManager.getInstance(project).getModificationTracker

  def getModificationCount: Long = {
    myRawModificationCount.get() + mainModificationTracker.getJavaStructureModificationCount
  }

  def incModificationCount(): Long = myRawModificationCount.incrementAndGet()
}