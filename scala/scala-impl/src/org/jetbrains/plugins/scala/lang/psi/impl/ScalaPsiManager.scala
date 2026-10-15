package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.util.{ModificationTracker, SimpleModificationTracker}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.JavaPsiFacadeImpl
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.util.PsiClassUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaLowerCase
import org.jetbrains.plugins.scala.caches.{CleanupScheduler, ScalaShortNamesCacheManager, ValueWrapper, cachedWithoutModificationCount}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaClassLookup}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.source.GlobalSearchScopeWithRecommendedResultsSorting
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticPackage, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TermNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScPackageObjectFqnIndex, ScPackagingFqnIndex, ScStableTypeAliasFqnIndex, ScalaIndexKeys}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.util
import java.util.concurrent.ConcurrentMap
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaPsiManager(override implicit val project: Project) extends ScalaClassLookup {

  private val inJavaPsiFacade: UnloadableThreadLocal[Boolean] = new UnloadableThreadLocal(false)

  def isInJavaPsiFacade: Boolean = inJavaPsiFacade.value

  private val caches = ScalaPsiCaches.instance(project)

  private def clearCacheOnTopLevelChange: CleanupScheduler = caches.clearCacheOnTopLevelChange

  override def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getPackageImplicitObjectsCached(fqn, scope).toSeq

  override def getTopLevelImplicitClassesByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScClass] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getTopLevelImplicitClassesByPackageCached(fqn, scope).toSeq

  override def getTopLevelGivenDefinitionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScGivenDefinition] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getTopLevelGivenDefinitionsByPackageCached(fqn, scope).toSeq

  def getTopLevelExtensionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScExtension] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getTopLevelExtensionsByPackageCached(fqn, scope).toSeq

  import ScalaIndexKeys._

  private val getPackageImplicitObjectsCached =
    cachedWithoutModificationCount(
      "getPackageImplicitObjectsCached",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (fqn: String, scope: GlobalSearchScope) => IMPLICIT_OBJECT_KEY.elements(cleanFqn(fqn), scope)
    )

  private val getTopLevelImplicitClassesByPackageCached =
    cachedWithoutModificationCount(
      "getTopLevelImplicitClassesByPackageCached",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (fqn: String, scope: GlobalSearchScope) => TOP_LEVEL_IMPLICIT_CLASS_BY_PKG_KEY.elements(cleanFqn(fqn), scope)
    )

  private val getTopLevelGivenDefinitionsByPackageCached =
    cachedWithoutModificationCount(
      "getTopLevelGivenDefinitionsByPackageCached",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (fqn: String, scope: GlobalSearchScope) => TOP_LEVEL_GIVEN_DEFINITIONS_BY_PKG_KEY.elements(cleanFqn(fqn), scope)
    )

  private val getTopLevelExtensionsByPackageCached =
    cachedWithoutModificationCount(
      "getTopLevelExtensionsByPackageCached",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (fqn: String, scope: GlobalSearchScope) => TOP_LEVEL_EXTENSION_BY_PKG_KEY.elements(cleanFqn(fqn), scope)
    )

  override def getCachedPackage(inFqn: String): Option[PsiPackage] = _getCachedPackage(inFqn)

  private val _getCachedPackage =
    cachedWithoutModificationCount(
      "getCachedPackage",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (inFqn: String) => {
        // to find java packages with scala keyword name as PsiPackage not ScSyntheticPackage
        val fqn = cleanFqn(inFqn)
        Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
      }
    )

  override def emptyNamePackage: Option[ScPackageImpl] = _noNamePackage()
  private[psi] def noNamePackage: ScPackageImpl = _noNamePackage().orNull

  private val _noNamePackage: () => Option[ScPackageImpl] =
    cachedWithoutModificationCount(
      "noNamePackage",
      ValueWrapper.SofterReference[Option[ScPackageImpl]],
      clearCacheOnTopLevelChange,
      () => getCachedPackage("").map(ScPackageImpl(_))
   )

  private def isPackageOutOfScope(
    `package`: PsiPackage
  )(
    implicit
    scope: GlobalSearchScope
  ): Boolean =
    `package`.getSubPackages(scope).isEmpty &&
      `package`.getClasses(scope).isEmpty

  private def isScalaPackageInScope(fqn: String)
                                         (implicit scope: GlobalSearchScope): Boolean = {
    ScPackagingFqnIndex.instance.hasElement(fqn, project, scope, classOf[ScPackaging]) ||
      ScPackageObjectFqnIndex.instance.hasElement(fqn, project, scope, classOf[PsiClass])
  }

  override def getCachedPackageInScope(
    fqn: String
  )(
    implicit
    scope: GlobalSearchScope
  ): Option[PsiPackage] =
    if (DumbService.getInstance(project).isDumb)
      None
    else
      computeCachedPackageInScope(fqn, scope)

  private val computeCachedPackageInScope =
    cachedWithoutModificationCount(
      "computeCachedPackageInScope",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (fqn: String, scope: GlobalSearchScope) =>
        getCachedPackage(fqn).filter { `package` =>
          isScalaPackageInScope(fqn)(using scope) || !isPackageOutOfScope(`package`)(using scope)
        }
    )

  override def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] =
    if (DumbService.getInstance(project).isDumb) None
    else _getCachedClass(scope, fqn)

  private val _getCachedClass =
    cachedWithoutModificationCount(
      "getCachedClass",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (scope: GlobalSearchScope, fqn: String) => {
        def getCachedFacadeClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] = {
          inJavaPsiFacade.value = true
          try {
            val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, scope)
            if (clazz == null || clazz.is[ScTemplateDefinition, PsiClassWrapper]) None
            else Option(clazz)
          } finally inJavaPsiFacade.value = false
        }

        val res = ScalaShortNamesCacheManager.getInstance(using project).getClassByFQName(fqn, scope)
        Option(res).orElse(getCachedFacadeClass(scope, fqn))
      }
    )

  def getTopLevelExportsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScExportStmt] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else _getTopLevelExportsByPackage(pkgFqn, scope)

  private val _getTopLevelExportsByPackage =
    cachedWithoutModificationCount(
      "getTopLevelDefinitionsByPackage",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (pkgFqn: String, scope: GlobalSearchScope) => {
        val fqn = cleanFqn(pkgFqn)
        TOP_LEVEL_EXPORT_BY_PKG_KEY.elements(fqn, scope)
      }
    )


  override def getTopLevelDefinitionsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScMember] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else _getTopLevelDefinitionsByPackage(pkgFqn, scope)

  private val _getTopLevelDefinitionsByPackage =
    cachedWithoutModificationCount(
      "getTopLevelDefinitionsByPackage",
      ValueWrapper.SofterReference,
      clearCacheOnTopLevelChange,
      (pkgFqn: String, scope: GlobalSearchScope) => {
        val fqn = cleanFqn(pkgFqn)
        TOP_LEVEL_VAL_OR_VAR_BY_PKG_KEY.elements(fqn, scope) ++
          TOP_LEVEL_TYPE_ALIAS_BY_PKG_KEY.elements(fqn, scope) ++
          TOP_LEVEL_FUNCTION_BY_PKG_KEY.elements(fqn, scope) ++
          TOP_LEVEL_EXTENSION_BY_PKG_KEY.elements(fqn, scope)
      }
    )

  def getTypeAliasesByName(name: String, scope: GlobalSearchScope): Iterable[ScTypeAlias] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else TYPE_ALIAS_NAME_KEY.elements(cleanFqn(name), scope)

  def getStableAliasesByFqn(fqn: String, scope: GlobalSearchScope): Iterable[ScTypeAlias] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else {
      val elements = ScStableTypeAliasFqnIndex.instance.getElements(fqn, project, scope).asScala
      elements.filter(_.qualifiedNameOpt.contains(fqn))
    }

  def getClassesByName(name: String, scope: GlobalSearchScope): Seq[PsiClass] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else {
      val scalaClasses = ScalaShortNamesCacheManager.getInstance(using project).getClassesByName(name, scope)
      val builder = ArraySeq.newBuilder[PsiClass]
      builder ++= PsiShortNamesCache
        .getInstance(project)
        .getClassesByName(name, scope)
        .iterator
        .filterNot(p => p.is[ScTemplateDefinition, PsiClassWrapper])
      val classesIterator = scalaClasses.iterator
      while (classesIterator.hasNext) {
        val clazz = classesIterator.next()
        builder += clazz
      }
      builder.result()
    }

  override def getClasses(
    `package`: PsiPackage
  )(
    implicit
    scope: GlobalSearchScope
  ): Array[PsiClass] =
    if (DumbService.getInstance(project).isDumb)
      PsiClass.EMPTY_ARRAY
    else
      `package`.getQualifiedName match {
        case ScalaLowerCase => getScalaPackageClassesCached(scope)
        case qualifiedName  => getJavaClasses(`package`) ++ getScalaClasses(qualifiedName)
      }

  private val getScalaPackageClassesCached =
    cachedWithoutModificationCount(
      "getScalaPackageClassesCached",
      ValueWrapper.None,
      clearCacheOnTopLevelChange,
      (scope: GlobalSearchScope) =>
        getScalaClassNamesCached(ScalaLowerCase, scope).flatMap { className =>
          getCachedClasses(scope, ScalaLowerCase + "." + className)
        }.toArray
    )

  private def getJavaClasses(
    `package`: PsiPackage
  )(
    implicit
    scope: GlobalSearchScope
  ) = {
    inJavaPsiFacade.value = true
    try
      JavaPsiFacade
        .getInstance(project)
        .asInstanceOf[JavaPsiFacadeImpl]
        .getClasses(`package`, scope)
        .filter {
          case _: ScTemplateDefinition | _: PsiClassWrapper => false
          case _                                            => true
        }
    finally
      inJavaPsiFacade.value = false
  }

  private def getScalaClasses(
    qualifiedName: String
  )(
    implicit
    scope: GlobalSearchScope
  ) = {
    val scalaQualifiedName = cleanFqn(qualifiedName)
    val toFqn =
      if (scalaQualifiedName.isEmpty)
        identity(_: String)
      else
        scalaQualifiedName + "." + (_: String)

    getScalaClassNamesCached(scalaQualifiedName, scope)
      .map(toFqn)
      .flatMap(getCachedClasses(scope, _))
  }

  override def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = _getCachedClasses(scope, fqn)

  private val _getCachedClasses = cachedWithoutModificationCount(
    "getCachedClasses",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (scope: GlobalSearchScope, fqn: String) => {
      def getCachedFacadeClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = {
        inJavaPsiFacade.value = true
        try {
          val classes = JavaPsiFacade
            .getInstance(project)
            .findClasses(
              fqn,
              new DelegatingGlobalSearchScope(scope) {
                override def compare(file1: VirtualFile, file2: VirtualFile): Int = 0
              }
            )
            .filterNot(_.is[ScTemplateDefinition, PsiClassWrapper])
          classes ++ SyntheticClassProducer.getAllClasses(fqn, scope)
        } finally inJavaPsiFacade.value = false
      }

      if (DumbService.getInstance(project).isDumb) Array.empty[PsiClass]
      else { // TODO Don't cache
        val classes = getCachedFacadeClasses(scope, cleanFqn(fqn))
        val fromScala = ScalaShortNamesCacheManager.getInstance(using project).getClassesByFQName(fqn, scope)
        val scalaSynthetic =
          if (fromScala.isEmpty) SyntheticClasses.get(project).findClasses(fqn)
          else Array.empty[PsiClass]
        val synthetic = SyntheticClassProducer.getAllClasses(fqn, scope)

        val result = Array.concat(fromScala.toArray, classes, synthetic, scalaSynthetic)
        scope match {
          case _: GlobalSearchScopeWithRecommendedResultsSorting =>
            util.Arrays.sort(result, PsiClassUtil.createScopeComparator(scope))
            result
          case _ =>
            result
        }
      }
    }
  )

  override def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] = _cachedFunction1Type(elementScope)

  private val _cachedFunction1Type = cachedWithoutModificationCount(
    "cachedFunction1Type",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (elementScope: ElementScope) => elementScope.function1Type()
  )

  override def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias] = _scalaSeqAlias(scope)

  private val _scalaSeqAlias = cachedWithoutModificationCount(
    "scalaSeqAlias",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (scope: GlobalSearchScope) => getStableAliasesByFqn("scala.Seq", scope).headOption
  )

  override def scalaNamedTupleAlias(scope: GlobalSearchScope): Option[ScTypeAlias] = _scalaNamedTupleAlias(scope)

  private val _scalaNamedTupleAlias = cachedWithoutModificationCount(
    "scalaNamedTupleAlias",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (scope: GlobalSearchScope) => getStableAliasesByFqn("scala.NamedTuple.NamedTuple", scope).headOption
  )

  def getJavaPackageClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set[String] = {
    if (DumbService.getInstance(project).isDumb) return Set.empty
    getJavaPackageClassNamesCached(psiPackage, scope)
  }

  private val getJavaPackageClassNamesCached = cachedWithoutModificationCount(
    "getJavaPackageClassNamesCached",
    ValueWrapper.None,
    clearCacheOnTopLevelChange,
    (psiPackage: PsiPackage, scope: GlobalSearchScope) => {
      val key = cleanFqn(psiPackage.getQualifiedName)
      val classes = JAVA_CLASS_NAME_IN_PACKAGE_KEY.elements(key, scope).toSet

      val additionalClasses = classes.flatMap {
        case definition: ScTypeDefinition => definition.additionalClassJavaName
        case _                            => None
      }

      classes.map(_.getName) ++ additionalClasses
    }
  )

  override def getScalaPackageClassNames(
    implicit
    scope: GlobalSearchScope
  ): Set[String] =
    if (DumbService.getInstance(project).isDumb) Set.empty
    else getScalaClassNamesCached(ScalaLowerCase, scope)

  private val getScalaClassNamesCached =
    cachedWithoutModificationCount(
      "getScalaClassNamesCached",
      ValueWrapper.None,
      clearCacheOnTopLevelChange,
      (scalaQualifiedName: String, scope: GlobalSearchScope) =>
        CLASS_NAME_IN_PACKAGE_KEY
          .elements(
            scalaQualifiedName,
            scope
          )
          .map(_.name)
          .toSet
    )

  private val syntheticPackages = ContainerUtil.createConcurrentWeakValueMap[String, AnyRef]()
  private val emptyMarker: AnyRef = ObjectUtils.sentinel("syntheticPackageEmptyMarker")

  clearCacheOnTopLevelChange.subscribe(() => syntheticPackages.clear())

  def syntheticPackage(fqn: String): ScSyntheticPackage = {
    val syntheticOrEmptyMarker =
      syntheticPackages.computeIfAbsent(fqn, fqn => Option(ScSyntheticPackage(fqn)(using project)).getOrElse(emptyMarker))

    syntheticOrEmptyMarker match {
      case s: ScSyntheticPackage => s
      case _                     => null
    }
  }

  def getStableTypeAliasesNames: Iterable[String] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else {
      import ScalaIndexKeys._
      STABLE_ALIAS_NAME_KEY.allKeys
    }

  // The type caches and modification tracking are in ScalaPsiCaches; these forward to it, for other plugins

  val TopLevelModificationTracker: SimpleModificationTracker = caches.TopLevelModificationTracker

  val rootManager: ModificationTracker = caches.rootManager

  val collectImplicitObjectsCache: ConcurrentMap[(ScType, GlobalSearchScope, Boolean), Seq[ScType]] =
    caches.collectImplicitObjectsCache

  val implicitCollectorCache: ImplicitCollectorCache = caches.implicitCollectorCache

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap =
    caches.getStableSignatures(tp, compoundTypeThisType)

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap =
    caches.getTypes(tp, compoundTypeThisType)

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap =
    caches.getSignatures(tp, compoundTypeThisType)

  def getStableSignatures(tp: ScAndType): PMap = caches.getStableSignatures(tp)

  def getTypes(tp: ScAndType): TMap = caches.getTypes(tp)

  def getSignatures(tp: ScAndType): SMap = caches.getSignatures(tp)

  def simpleAliasProjectionCached(projection: ScProjectionType): ScType = caches.simpleAliasProjectionCached(projection)

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType =
    caches.javaPsiTypeParameterUpperType(typeParameter)

  def clearOnScalaElementChange(psiElement: PsiElement): Unit = caches.clearOnScalaElementChange(psiElement)

  def clearOnNonScalaChange(): Unit = caches.clearOnNonScalaChange()

  def clearAllCaches(): Unit = caches.clearAllCaches()

  def clearAllCachesAndWait(): Unit = caches.clearAllCachesAndWait()

  @TestOnly
  def clearCachesOnChange(): Unit = caches.clearCachesOnChange()
}

object ScalaPsiManager {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager")

  def instance(
    implicit
    ctx: ProjectContext,
    d:   DummyImplicit
  ): ScalaPsiManager =
    instance(ctx.getProject)

  def instance(project: Project): ScalaPsiManager =
    project.getService(classOf[ScalaPsiManager])

  final class ClassLookupProvider extends ScalaClassLookup.Provider {
    override def lookup(project: Project): ScalaClassLookup = instance(project)
  }

  class SPMModuleRootListener(project: Project) extends ModuleRootListener {
    override def rootsChanged(event: ModuleRootEvent): Unit = {
      LOG.debug("Clear caches on root change")
      ScalaPsiCaches.instance(project).clearOnRootsChange()
    }
  }
}
