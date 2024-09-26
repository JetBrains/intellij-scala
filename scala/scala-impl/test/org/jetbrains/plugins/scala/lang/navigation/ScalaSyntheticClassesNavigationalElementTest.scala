package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.openapi.util.registry.Registry
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
import org.junit.Assert.assertTrue

/**
 * See also [[org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProviderTest_SyntheticScalaLibraryElements]]
 */
//noinspection ScalaWrongPlatformMethodsUsage
class ScalaSyntheticClassesNavigationalElementTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5

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

  private def getQualifiedName(element: PsiElement): String = {
    val provider = new ScalaQualifiedNameProvider
    provider.getQualifiedName(element)
  }

  private def allSubPackages(p: PsiPackage): Seq[PsiPackage] = {
    val packages = p.getSubPackages.toSeq
    packages ++ packages.flatMap(allSubPackages)
  }

  def testNavigationElementOfAllScalaLibraryDefinitionsPointsToSourceElement_TopLevel(): Unit = {
    val allPackages: Seq[PsiPackage] = allScalaLibraryPackages

    val scope = GlobalSearchScope.everythingScope(getProject)
    val manager = ScalaPsiManager.instance(getProject)

    val allScalaClasses = allPackages
      .flatMap(manager.getClasses(_)(scope))
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
      .flatMap(manager.getClasses(_)(scope))
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
      .filterNot(isExceptionWithNonWorkingNavigation)

    //TODO: ideally we shouldn't disable the filter, see SCL-22994
    Registry.get("ast.loading.filter").setValue(false, getTestRootDisposable)
    assertNavigationElementPointsToSources(allNonPrivateMembers)
  }

  //Filter out some synthetic internal methods and methods for which navigation doesn't work yet
  private def isExceptionWithNonWorkingNavigation(m: PsiElement): Boolean = {
    val qualifiedName = getQualifiedName(m)
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

  //TODO: remove when SCL-23058 is fixed
  private val MutedDefinitions: Set[String] = Set(
    "scala.NamedTuple.Names",
    "scala.NamedTuple.DropNames",
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
  )

  private def allScalaLibraryPackages: Seq[PsiPackage] = {
    val scalaPackage = ScPackageImpl.findPackage(getProject, "scala").get
    scalaPackage +: allSubPackages(scalaPackage)
  }

  def testNavigationElementPointsToSourceElement_Scala2SyntheticLibraryClasses(): Unit = {
    val sharedClasses = SyntheticClasses.get(getProject).sharedClassesOnly.filterByType[ScSyntheticClass].toSeq
    assertNavigationElementPointsToSources(sharedClasses)
  }

  def testNavigationElementPointsToSourceElement_Scala3SyntheticLibraryClasses(): Unit = {
    val scala3SyntheticClasses = SyntheticClasses.get(getProject).scala3ClassesOnly.toSeq
    val scala3SyntheticClassesWithExpectedSources = scala3SyntheticClasses.filter { c =>
      val fqn = c.getQualifiedName
      !fqn.startsWith("scala.ContextFunction") //scala.ContextFunctionN classes don't have any sources
    }
    assertNavigationElementPointsToSources(scala3SyntheticClassesWithExpectedSources)
  }

  def testNavigationElementPointsToSourceElement_SyntheticAliases(): Unit = {
    val syntheticObjects = SyntheticClasses.get(getProject).aliases
    syntheticObjects.foreach(assertNavigationElementPointsToSources)
  }
}