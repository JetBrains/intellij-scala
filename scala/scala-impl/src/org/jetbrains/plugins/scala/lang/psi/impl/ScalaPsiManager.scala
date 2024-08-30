package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener, ProjectRootManager}
import com.intellij.openapi.util._
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.JavaPsiFacadeImpl
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.psi.util.PsiClassUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.ScalaLowerCase
import org.jetbrains.plugins.scala.caches.stats.{CacheCapabilities, CacheTracker}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, CleanupScheduler, ModTracker, ScalaModificationTracker, ScalaShortNamesCacheManager, ValueWrapper, cachedInUserData, cachedWithoutModificationCount}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.idToName
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager._
import org.jetbrains.plugins.scala.lang.psi.impl.source.GlobalSearchScopeWithRecommendedResultsSorting
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticPackage, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.StableNodes.{Map => PMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TermNodes.{Map => SMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.TypeNodes.{Map => TMap}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.{StableNodes, TermNodes, TypeNodes}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollectorCache
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ScPackageObjectFqnIndex, ScPackagingFqnIndex, ScStableTypeAliasFqnIndex, ScalaIndexKeys}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, ParameterizedType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil._
import org.jetbrains.plugins.scala.lang.resolve.SyntheticClassProducer
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.util
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaPsiManager(implicit val project: Project) extends Disposable {

  private val inJavaPsiFacade: UnloadableThreadLocal[Boolean] = new UnloadableThreadLocal(false)

  def isInJavaPsiFacade: Boolean = inJavaPsiFacade.value

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

  def getStableSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): PMap =
    if (dontCacheCompound) StableNodes.build(tp, compoundTypeThisType)
    else getStableSignaturesCached(tp, compoundTypeThisType)

  private val getStableSignaturesCached =
    cachedWithoutModificationCount(
      "getStableSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => StableNodes.build(tp, compoundTypeThisType)
    )

  def getTypes(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): TMap =
    if (dontCacheCompound) TypeNodes.build(tp, compoundTypeThisType)
    else getTypesCached(tp, compoundTypeThisType)

  private val getTypesCached =
    cachedWithoutModificationCount(
      "getTypesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => TypeNodes.build(tp, compoundTypeThisType)
    )

  def getSignatures(tp: ScCompoundType, compoundTypeThisType: Option[ScType]): SMap = {
    if (dontCacheCompound) return TermNodes.build(tp, compoundTypeThisType)
    getSignaturesCached(tp, compoundTypeThisType)
  }

  private val getSignaturesCached =
    cachedWithoutModificationCount(
      "getSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScCompoundType, compoundTypeThisType: Option[ScType]) => TermNodes.build(tp, compoundTypeThisType)
    )

  def getStableSignatures(tp: ScAndType): PMap =
    if (dontCacheCompound) StableNodes.build(tp)
    else getIntersectionStableSignaturesCached(tp)

  private val getIntersectionStableSignaturesCached =
    cachedWithoutModificationCount(
      "getIntersectionStableSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => StableNodes.build(tp)
    )

  def getTypes(tp: ScAndType): TMap =
    if (dontCacheCompound) TypeNodes.build(tp)
    else getIntersectionTypesCached(tp)

  private val getIntersectionTypesCached =
    cachedWithoutModificationCount(
      "getIntersectionTypesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => TypeNodes.build(tp)
    )

  def getSignatures(tp: ScAndType): SMap = {
    if (dontCacheCompound) return TermNodes.build(tp)
    getIntersectionSignaturesCached(tp)
  }

  private val getIntersectionSignaturesCached =
    cachedWithoutModificationCount(
      "getIntersectionSignaturesCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (tp: ScAndType) => TermNodes.build(tp)
    )

  def simpleAliasProjectionCached(projection: ScProjectionType): ScType = _simpleAliasProjectionCached(projection)

  private val _simpleAliasProjectionCached =
    cachedWithoutModificationCount(
      "simpleAliasProjectionCached",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (projection: ScProjectionType) => ScProjectionType.simpleAliasProjection(projection)
    )

  def getPackageImplicitObjects(fqn: String, scope: GlobalSearchScope): Seq[ScObject] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getPackageImplicitObjectsCached(fqn, scope).toSeq

  def getTopLevelImplicitClassesByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScClass] =
    if (DumbService.getInstance(project).isDumb) Seq.empty
    else getTopLevelImplicitClassesByPackageCached(fqn, scope).toSeq

  def getTopLevelGivenDefinitionsByPackage(fqn: String, scope: GlobalSearchScope): Seq[ScGivenDefinition] =
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

  def getCachedPackage(inFqn: String): Option[PsiPackage] = _getCachedPackage(inFqn)

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

  private[psi] def noNamePackage: ScPackageImpl = _noNamePackage().orNull

  private val _noNamePackage: () => Option[ScPackageImpl] =
    cachedWithoutModificationCount(
      "noNamePackage",
      ValueWrapper.SofterReference[Option[ScPackageImpl]],
      clearCacheOnTopLevelChange,
      () => getCachedPackage("").map(ScPackageImpl(_))
   )

  private[this] def isPackageOutOfScope(
    `package`: PsiPackage
  )(
    implicit
    scope: GlobalSearchScope
  ): Boolean =
    `package`.getSubPackages(scope).isEmpty &&
      `package`.getClasses(scope).isEmpty

  private[this] def isScalaPackageInScope(fqn: String)
                                         (implicit scope: GlobalSearchScope): Boolean = {
    ScPackagingFqnIndex.instance.hasElement(fqn, project, scope, classOf[ScPackaging]) ||
      ScPackageObjectFqnIndex.instance.hasElement(fqn, project, scope, classOf[PsiClass])
  }

  def getCachedPackageInScope(
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
          isScalaPackageInScope(fqn)(scope) || !isPackageOutOfScope(`package`)(scope)
        }
    )

  def getCachedClass(scope: GlobalSearchScope, fqn: String): Option[PsiClass] =
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
            if (clazz == null || clazz.is[ScTemplateDefinition] || clazz.is[PsiClassWrapper]) None
            else Option(clazz)
          } finally inJavaPsiFacade.value = false
        }

        val res = ScalaShortNamesCacheManager.getInstance(project).getClassByFQName(fqn, scope)
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


  def getTopLevelDefinitionsByPackage(pkgFqn: String, scope: GlobalSearchScope): Iterable[ScMember] =
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
      val scalaClasses = ScalaShortNamesCacheManager.getInstance(project).getClassesByName(name, scope)
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

  def getClasses(
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

  private[this] val getScalaPackageClassesCached =
    cachedWithoutModificationCount(
      "getScalaPackageClassesCached",
      ValueWrapper.None,
      clearCacheOnTopLevelChange,
      (scope: GlobalSearchScope) =>
        getScalaClassNamesCached(ScalaLowerCase, scope).flatMap { className =>
          getCachedClasses(scope, ScalaLowerCase + "." + className)
        }.toArray
    )

  private[this] def getJavaClasses(
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

  private[this] def getScalaClasses(
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

  def getCachedClasses(scope: GlobalSearchScope, fqn: String): Array[PsiClass] = _getCachedClasses(scope, fqn)

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
            .filterNot(p => p.is[ScTemplateDefinition] || p.is[PsiClassWrapper])

          val syntheticClasses = SyntheticClassProducer.getAllClasses(fqn, scope)
          classes ++ syntheticClasses
        } finally {
          inJavaPsiFacade.value = false
        }
      }

      if (DumbService.getInstance(project).isDumb) Array.empty[PsiClass]
      else { // TODO Don't cache
        val classes = getCachedFacadeClasses(scope, cleanFqn(fqn))
        val fromScala = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(fqn, scope)
        val scalaSynthetic = SyntheticClasses.get(scope.getProject).findClasses(fqn)
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


  /**
   * @param name short name of an AnyVal class. Examples: Int, Boolean, Unit
   */
  def getScalaLibraryAnyValClass(scope: GlobalSearchScope, name: String): Option[PsiClass] =
    getScalaLibraryAnyValClassCached(scope, name)

  private val getScalaLibraryAnyValClassCached: (GlobalSearchScope, String) => Option[PsiClass] = cachedWithoutModificationCount(
    "getScalaLibraryAnyValClassCached",
    // don't use soft reference, there should be not that many std lib classes
    // (even if we have 1000 modules, it would be just 8000 entries, which shouldn't be that bad)
    ValueWrapper.None,
    // Invalidate the cache on project root model changes.
    // We expect that definitions from the scala library can change only on project structure change
    // (for example, when some module Scala version has changed).
    // The difference between `getScalaClassNamesCached` is that this is not recalculated on every top-level change.
    clearCacheOnRootsChange,
    (scope: GlobalSearchScope, name: String) => {
      val qualifiedName = s"scala.$name"
      val manager = ScalaShortNamesCacheManager.getInstance(scope.getProject)
      Option(manager.getClassByFQName(qualifiedName, scope))
    }
  )

  def cachedFunction1Type(elementScope: ElementScope): Option[ScParameterizedType] = _cachedFunction1Type(elementScope)

  private val _cachedFunction1Type = cachedWithoutModificationCount(
    "cachedFunction1Type",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (elementScope: ElementScope) => elementScope.function1Type()
  )

  def scalaSeqAlias(scope: GlobalSearchScope): Option[ScTypeAlias] = _scalaSeqAlias(scope)

  private val _scalaSeqAlias = cachedWithoutModificationCount(
    "scalaSeqAlias",
    ValueWrapper.SofterReference,
    clearCacheOnTopLevelChange,
    (scope: GlobalSearchScope) => getStableAliasesByFqn("scala.Seq", scope).headOption
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

  def getScalaPackageClassNames(
    implicit
    scope: GlobalSearchScope
  ): Set[String] =
    if (DumbService.getInstance(project).isDumb) Set.empty
    else getScalaClassNamesCached(ScalaLowerCase, scope)

  private[this] val getScalaClassNamesCached =
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

  private val syntheticPackages = ContainerUtil.createConcurrentWeakValueMap[String, AnyRef]()
  private val emptyMarker: AnyRef = ObjectUtils.sentinel("syntheticPackageEmptyMarker")

  def syntheticPackage(fqn: String): ScSyntheticPackage = {
    val syntheticOrEmptyMarker =
      syntheticPackages.computeIfAbsent(fqn, fqn => Option(ScSyntheticPackage(fqn)(project)).getOrElse(emptyMarker))

    syntheticOrEmptyMarker match {
      case s: ScSyntheticPackage => s
      case _                     => null
    }
  }

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = _javaPsiTypeParameterUpperType(
    typeParameter
  )

  private val _javaPsiTypeParameterUpperType =
    cachedWithoutModificationCount(
      "javaPsiTypeParameterUpperType",
      ValueWrapper.SofterReference,
      clearCacheOnChange,
      (typeParameter: PsiTypeParameter) => {
        val types = ArraySeq.unsafeWrapArray(typeParameter.getExtendsListTypes ++ typeParameter.getImplementsListTypes)
        types match {
          case Seq()                                                           => Any
          case Seq(one) if one.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) =>
            // Generics with upper bound java.lang.Object should have the upper bound scala.Any in Scala
            Any
          case _ => andType(types)
        }
      }
    )

  private def andType(psiTypes: Seq[PsiType]): ScType =
    new ProjectContext(project).typeSystem.andType(psiTypes.map(_.toScType()))

  def getStableTypeAliasesNames: Iterable[String] =
    if (DumbService.getInstance(project).isDumb) Iterable.empty
    else {
      import ScalaIndexKeys._
      STABLE_ALIAS_NAME_KEY.allKeys
    }

  val TopLevelModificationTracker: SimpleModificationTracker = new ScalaModificationTracker("TopLevelModificationTracker")

  def clearOnScalaElementChange(psiElement: PsiElement): Unit = {
    clearOnChange()
    updateScalaModificationCount(psiElement)
  }

  def clearOnNonScalaChange(): Unit = {
    clearOnTopLevelChange()
    TopLevelModificationTracker.incModificationCount()
  }

  @tailrec
  private def updateScalaModificationCount(@Nullable element: PsiElement): Unit = element match {
    case null =>
      TopLevelModificationTracker.incModificationCount()
      clearOnTopLevelChange()
    case owner: ScExpression if BlockModificationTracker.hasStableType(owner) =>
      BlockModificationTracker.incrementLocalCounter(owner)
    case _ =>
      updateScalaModificationCount(element.getContext)
  }

  val rootManager: ModificationTracker = ProjectRootManager.getInstance(project)

  sealed abstract class SignatureCaches[T <: Signature](val nodes: MixinNodes[T]) {

    private val forLibraryMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map] = ContainerUtil.createConcurrentWeakMap()
    private val forTopLevelMap: ConcurrentMap[(PsiClass, Boolean), nodes.Map] = ContainerUtil.createConcurrentWeakMap()

    clearCacheOnRootsChange.subscribe(() => forLibraryMap.clear())
    clearCacheOnTopLevelChange.subscribe(() => forTopLevelMap.clear())

    private def forLibraryClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forLibraryMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    private def forTopLevelClasses(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      forTopLevelMap.computeIfAbsent((clazz, withSupers), { case (cls, supers) => nodes.build(cls, supers) })

    def cachedMap(clazz: PsiClass, withSupers: Boolean): nodes.Map =
      ModTracker.libraryAware(clazz) match {
        case `rootManager`               => forLibraryClasses(clazz, withSupers)
        case TopLevelModificationTracker => forTopLevelClasses(clazz, withSupers)
        case tracker =>
          cachedInUserData("cachedMap", clazz, tracker, (clazz, nodes, withSupers)) {
            nodes.build(clazz, withSupers)
          }
      }
  }

  object TermNodesCache extends SignatureCaches(TermNodes)
  object StableNodesCache extends SignatureCaches(StableNodes)
  object TypeNodesCache extends SignatureCaches(TypeNodes)

  def clearAllCaches(): Unit = invokeLater {
    doClearAllCaches()
  }

  def clearAllCachesAndWait(): Unit = invokeAndWait {
    doClearAllCaches()
  }

  @RequiresEdt()
  private def doClearAllCaches(): Unit =
    if (!project.isDisposed) {
      clearOnRootsChange()
      TopLevelModificationTracker.incModificationCount()
    }

  locally {
    LowMemoryWatcher.register(
      () => {
        LOG.debug("Clear caches on low memory")
        clearOnLowMemory()
      },
      this
    )
  }

  override def dispose(): Unit = {
    clearAllCaches()
  }

  @TestOnly
  def clearCachesOnChange(): Unit =
    clearOnChange()
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

  implicit val ImplicitCollectorCacheCapabilities: CacheCapabilities[ImplicitCollectorCache] =
    new CacheCapabilities[ImplicitCollectorCache] {
      override def cachedEntitiesCount(cache: CacheType): Int = cache.size()
      override def clear(cache:               CacheType): Unit = cache.clear()
    }

  class SPMModuleRootListener(project: Project) extends ModuleRootListener {
    override def rootsChanged(event: ModuleRootEvent): Unit = {
      LOG.debug("Clear caches on root change")
      ScalaPsiManager.instance(project).clearOnRootsChange()
    }
  }
}
