package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.{LibraryOrderEntry, ModuleRootManager, OrderEntry, OrderRootType}
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.actions.ScalaQualifiedNameProvider
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScExtension, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticClass, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.junit.Assert.{assertEquals, assertTrue}

/**
 * See also [[org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProviderTest_SyntheticScalaLibraryElements]]
 */
//noinspection ScalaWrongPlatformMethodsUsage
abstract class ScalaSyntheticClassesNavigationalElementTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def includeScalaLibrarySources: Boolean = true

  protected def assertNavigationElementPointsToSources(elements: Seq[PsiElement]): Unit = {
    val fileToElements = elements.groupBy(_.getContainingFile.asInstanceOf[ScalaFile]).toSeq.sortBy(_._1.getName)

    var idx = 1
    val elementsTotal = elements.size
    for {
      (file, elements) <- fileToElements
      element <- elements
    } {
      val wasStubBasedBefore = file.getStubTree != null

      val virtualFile = file.getViewProvider.getVirtualFile
      val filePath = virtualFile.getPath
      val filePathCompact = filePath.substring(filePath.indexOf("scala/").max(0))
      val displayableName = getQualifiedName(element)
      println(s"[$idx / $elementsTotal] file: $filePathCompact, element: $displayableName")

      val isStubBasedAfter = file.getStubTree != null
      val astWasNotLoadedOrIsSynthetic = isStubBasedAfter == wasStubBasedBefore || !file.isPhysical
      assertTrue(
        s"File $filePath is supposed to be stub-based. All preliminary operations shouldn't load AST",
        astWasNotLoadedOrIsSynthetic
      )

      assertNavigationElementPointsToSources(element)

      idx += 1
    }
  }

  protected def assertNavigationElementPointsToSources(element: PsiElement): Unit = {
    val navigationElement = element.getNavigationElement
    val navigationElementFile = navigationElement.getContainingFile.getViewProvider.getVirtualFile
    val isFromSources = navigationElement != element && navigationElementFile.getPath.contains("-sources.jar")
    val displayableName = getQualifiedName(element)
    assertTrue(
      s"${element.getClass.getSimpleName}.getNavigationElement of `$displayableName` should return element from sources, not the original element (actual file: $navigationElementFile)",
      isFromSources,
    )
  }

  /**
   * Names members for test selection and exclusions, as well as diagnostics.
   * An incorrect name can change which members the navigation assertions check.
   * Synthetic classes use their PSI qualified name instead of the general name provider.
   */
  private def getQualifiedName(element: PsiElement): String = {
    element match {
      case cls: ScSyntheticClass => cls.getQualifiedName
      case _ => new ScalaQualifiedNameProvider().getQualifiedName(element)
    }
  }

  private lazy val attachedSourceRoots: Seq[VirtualFile] = {
    val orderEntries = ModuleRootManager.getInstance(getModule).getOrderEntries.toSeq
    val libraries = orderEntries.flatMap(extractLibrary)
    libraries.flatMap(_.getFiles(OrderRootType.SOURCES))
  }

  private def extractLibrary(entry: OrderEntry): Option[Library] = entry match {
    case libraryEntry: LibraryOrderEntry => Option(libraryEntry.getLibrary)
    case _ => None
  }

  private def hasAttachedScalaSourceFile(fileName: String): Boolean =
    attachedSourceRoots.exists(_.findFileByRelativePath(s"scala/$fileName") != null)

  private case class MissingSourceExpectations(
    scala2SyntheticClasses: Set[String],
    scala3SyntheticClasses: Set[String],
    syntheticAliasFiles: Set[String],
  )

  private val Scala35To310ContextFunctionsWithoutSources = (1 to 22).map(n => s"scala.ContextFunction$n").toSet

  private val Scala35MissingSources = MissingSourceExpectations(
    scala2SyntheticClasses = Set.empty,
    scala3SyntheticClasses = Scala35To310ContextFunctionsWithoutSources,
    syntheticAliasFiles = Set.empty,
  )

  // Scala 3.8+ source jars omit Scala 2 bootstrap sources and some compiler-defined types.
  private val Scala38To310MissingSources = MissingSourceExpectations(
    scala2SyntheticClasses = Set(
      "scala.Any",
      "scala.AnyRef",
      "scala.Nothing",
      "scala.Null",
      "scala.Singleton",
    ),
    scala3SyntheticClasses = Scala35To310ContextFunctionsWithoutSources ++ Set(
      "scala.AnyKind",
      "scala.Matchable",
    ),
    syntheticAliasFiles = Set(
      "andType.scala",
      "orType.scala",
    ),
  )

  private val SyntheticAliasSourceFileNames = Set(
    "andType.scala",
    "orType.scala",
  )

  private val MissingSourcesByVersion = Map(
    ScalaVersion.Latest.Scala_3_5 -> Scala35MissingSources,
    ScalaVersion.Latest.Scala_3_8 -> Scala38To310MissingSources,
    ScalaVersion.Latest.Scala_3_9 -> Scala38To310MissingSources,
    ScalaVersion.Latest.Scala_3_10 -> Scala38To310MissingSources,
  )

  private def expectedMissingSourcesFor(version: ScalaVersion): MissingSourceExpectations =
    MissingSourcesByVersion.getOrElse(version,
      throw new AssertionError(s"Review missing-source expectations for Scala ${version.minor}"))

  private def allSubPackages(p: PsiPackage): Seq[PsiPackage] = {
    val packages = p.getSubPackages.toSeq
    packages ++ packages.flatMap(allSubPackages)
  }

  def testNavigationElementOfAllScalaLibraryDefinitionsPointsToSourceElement_TopLevel(): Unit = {
    val allPackages: Seq[PsiPackage] = allScalaLibraryPackages

    val scope = GlobalSearchScope.everythingScope(getProject)
    val manager = ScalaPsiManager.instance(getProject)

    val allScalaClasses = allPackages
      .flatMap(manager.getClasses(_)(using scope))
      // this is an exceptional class in the standard library which is defined in `scala/reflect/package.scala`
      // but has package `scala` so we can't detect the original source file
      .filterNot(_.qualifiedName.contains("ScalaReflectionException"))
      .filter(_.isInstanceOf[ScalaPsiElement])

    val allScalaTopLevelDefinitions: Seq[PsiElement] = allPackages
      .flatMap(p => manager.getTopLevelDefinitionsByPackage(p.getQualifiedName, scope))
      .flatMap {
        case v: ScValueOrVariable => v.declaredElements
        case e: ScExtension => e.declaredElements
        case m => Seq(m)
      }

    //ensure we find some classes which we are explicitly interested in
    assertTrue(allScalaClasses.exists(_.getQualifiedName == "scala.Boolean"))
    assertTrue(allScalaClasses.exists(_.getQualifiedName == "scala.Unit"))
    assertTrue(allScalaClasses.exists(_.getQualifiedName == "scala.Unit$"))

    val qualifiedNamedProvider = new ScalaQualifiedNameProvider
    assertTrue(allScalaTopLevelDefinitions.exists(el => qualifiedNamedProvider.getQualifiedName(el) == "scala.compiletime.asMatchable"))
    assertTrue(allScalaTopLevelDefinitions.exists(el => qualifiedNamedProvider.getQualifiedName(el) == "scala.compiletime.error"))

    val allElements = allScalaClasses ++ allScalaTopLevelDefinitions
    assertNavigationElementPointsToSources(allElements)
  }

  def testNavigationElementOfAllScalaLibraryDefinitionsPointsToSourceElement_NonTopLevel_NonPrivate(): Unit = {
    val allPackages: Seq[PsiPackage] = allScalaLibraryPackages

    val scope = GlobalSearchScope.everythingScope(getProject)
    val manager = ScalaPsiManager.instance(getProject)

    val allScalaClasses = allPackages
      .flatMap(manager.getClasses(_)(using scope))
      // this is an exceptional class in the standard library which is defined in `scala/reflect/package.scala`
      // but has package `scala` so we can't detect the original source file
      .filterNot(_.qualifiedName.contains("ScalaReflectionException"))
      .filterByType[ScTypeDefinition]

    val allNonPrivateMembers = allScalaClasses
      .flatMap(_.members)
      .filterNot(_.isPrivate)
      .flatMap {
        case d: ScDeclaredElementsHolder => d.declaredElements
        case m => Seq(m)
      }

    val expectedNewerLibraryExclusions = if (version >= ScalaVersion.Latest.Scala_3_8)
      Set("scala.NamedTuple.head", "scala.NamedTuple.toSeqMap")
    else
      Set.empty[String]
    val expectedScala310Exclusions = if (version >= ScalaVersion.Latest.Scala_3_10)
      Set("scala.collection.generic.IsSeq.iarrayIsSeq", "scala.NamedTuple.namedTupleCanEqual")
    else
      Set.empty[String]
    val expectedExclusions = expectedNewerLibraryExclusions ++ expectedScala310Exclusions
    assertEquals("Known source-mirror exclusions should be reviewed when library definitions change",
      expectedExclusions, allNonPrivateMembers.map(getQualifiedName).toSet.intersect(expectedExclusions))
    val checkedMembers = allNonPrivateMembers.filterNot(isExceptionWithNonWorkingNavigation)

    //TODO: ideally we shouldn't disable the filter, see SCL-22994
    Registry.get("ast.loading.filter").setValue(false, getTestRootDisposable)
    assertNavigationElementPointsToSources(checkedMembers)
  }

  //Filter out some synthetic internal methods and methods for which navigation doesn't work yet
  private def isExceptionWithNonWorkingNavigation(m: PsiElement): Boolean = {
    val qualifiedName = getQualifiedName(m)
    // TODO SCL-23058: Review and remove muted definitions as their source navigation is fixed.
    if (MutedDefinitions.contains(qualifiedName))
      return true

    m match {
      case m: ScMember =>
        //Scala 2 compiler generates some extra synthetic methods with "$extension" suffix for scala2-style extension methods.
        //(you can see it when viewing the decompiled version)
        //Examples:
        //  scala.collection.ArrayOps#elemTag$extension
        //  scala.runtime.Tuple3Zipped#coll1$extension
        //These methods are considered an implementation detail.
        //They can't be accessed from Scala code anyway (though they can be accessed from Java code).
        //Thus, we do not handle them
        Option(m.getName).exists(_.endsWith("$extension"))
      case _ =>
        false
    }
  }

  private val MutedDefinitions: Set[String] = Set(
    "scala.NamedTuple.Names",
    "scala.NamedTuple.DropNames",
    "scala.NamedTuple.head",
    "scala.NamedTuple.apply",
    "scala.NamedTuple.size",
    "scala.NamedTuple.init",
    "scala.NamedTuple.last",
    "scala.NamedTuple.tail",
    "scala.NamedTuple.take",
    "scala.NamedTuple.drop",
    "scala.NamedTuple.splitAt",
    "scala.NamedTuple.++",
    "scala.NamedTuple.map",
    "scala.NamedTuple.reverse",
    "scala.NamedTuple.zip",
    "scala.NamedTuple.toList",
    "scala.NamedTuple.toArray",
    "scala.NamedTuple.toIArray",
    "scala.NamedTuple.toSeqMap",
    // The compiled structural given has a source-level given alias, which is not a type-definition mirror.
    "scala.collection.generic.IsSeq.iarrayIsSeq",
    // The Scala 3.10 source given alias does not have a matching compiled source mirror yet.
    "scala.NamedTuple.namedTupleCanEqual",
  )

  private def allScalaLibraryPackages: Seq[PsiPackage] = {
    val scalaPackage = ScPackageImpl.findPackage(getProject, "scala").get
    scalaPackage +: allSubPackages(scalaPackage)
  }

  def testNavigationElementPointsToSourceElement_Scala2SyntheticLibraryClasses(): Unit = {
    val sharedClasses = SyntheticClasses.get(getProject).sharedClassesOnly.filterByType[ScSyntheticClass].toSeq
    assertEquals("Shared synthetic class inventory", 15, sharedClasses.size)
    val expectedWithoutSources = expectedMissingSourcesFor(version).scala2SyntheticClasses
    val (excluded, withSources) = sharedClasses.partition(c => !hasAttachedScalaSourceFile(s"${c.className}.scala"))
    assertEquals("Shared synthetic classes without attached source files",
      expectedWithoutSources, excluded.map(_.getQualifiedName).toSet)
    assertNavigationElementPointsToSources(withSources)
  }

  def testNavigationElementPointsToSourceElement_Scala3SyntheticLibraryClasses(): Unit = {
    val scala3SyntheticClasses = SyntheticClasses.get(getProject).scala3ClassesOnly.toSeq
    val expectedWithoutSources = expectedMissingSourcesFor(version).scala3SyntheticClasses
    val (excluded, withSources) = scala3SyntheticClasses.partition(c => !hasAttachedScalaSourceFile(s"${c.getName}.scala"))
    assertEquals("Scala 3 synthetic classes without attached source files",
      expectedWithoutSources, excluded.map(_.getQualifiedName).toSet)
    assertEquals("Scala 3 synthetic class inventory", 24, scala3SyntheticClasses.size)
    assertNavigationElementPointsToSources(withSources)
  }

  def testNavigationElementPointsToSourceElement_SyntheticAliases(): Unit = {
    val aliases = SyntheticClasses.get(getProject).aliases.toSeq
    assertEquals("Synthetic alias inventory", Set("&", "|"), aliases.map(_.name).toSet)
    val expectedMissingSourceFiles = expectedMissingSourcesFor(version).syntheticAliasFiles
    val missingSourceFiles = SyntheticAliasSourceFileNames.filterNot(hasAttachedScalaSourceFile)
    assertEquals("Synthetic alias source files", expectedMissingSourceFiles, missingSourceFiles)
    if (missingSourceFiles.isEmpty)
      aliases.foreach(assertNavigationElementPointsToSources)
  }
}

class ScalaSyntheticClassesNavigationalElementTest_Scala_3_5 extends ScalaSyntheticClassesNavigationalElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class ScalaSyntheticClassesNavigationalElementTest_Scala_3_8 extends ScalaSyntheticClassesNavigationalElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8
}

class ScalaSyntheticClassesNavigationalElementTest_Scala_3_9 extends ScalaSyntheticClassesNavigationalElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_9
}

class ScalaSyntheticClassesNavigationalElementTest_Scala_3_10 extends ScalaSyntheticClassesNavigationalElementTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_10
}