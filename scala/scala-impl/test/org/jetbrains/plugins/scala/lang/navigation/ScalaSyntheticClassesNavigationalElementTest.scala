package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.psi.PsiMember
import org.jetbrains.plugins.scala.DependencyManagerBase.Resolver
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaLibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.navigation.ScalaSyntheticClassesNavigationalElementTest.libraryLoadersWithSeparateScalaLibraries
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.junit.Assert.assertTrue

class ScalaSyntheticClassesNavigationalElementTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def librariesLoaders: Seq[LibraryLoader] =
    libraryLoadersWithSeparateScalaLibraries(super.librariesLoaders)

  protected def assertNavigationElementPointsToSources(element: PsiMember): Unit = {
    val navigationElement = element.getNavigationElement
    val navigationElementFile = navigationElement.getContainingFile.getViewProvider.getVirtualFile
    assertTrue(
      s"${element.getClass.getSimpleName}.getNavigationElement of class ${element.qualifiedNameOpt.getOrElse(element.getName)} should return element from sources, not the original element (actual file: $navigationElementFile)",
      navigationElement != element && navigationElementFile.getPath.contains("-sources.jar"),
    )
  }

  def testThatNavigationElementPointsToSourceElement_Scala2SyntheticLibraryClasses(): Unit = {
    val scala3SyntheticClasses = SyntheticClasses.get(getProject).sharedClassesOnly
    scala3SyntheticClasses.foreach(assertNavigationElementPointsToSources)
  }

  def testThatNavigationElementPointsToSourceElement_Scala3SyntheticLibraryClasses(): Unit = {
    val scala3SyntheticClasses = SyntheticClasses.get(getProject).getScala3Classes.toSeq
    val scala3SyntheticClassesWithExpectedSources = scala3SyntheticClasses.filter { c =>
      val fqn = c.getQualifiedName
      !fqn.startsWith("scala.ContextFunction") && //scala.ContextFunctionN classes don't have any sources
        //TODO: remove filtering and fix implementation if needed
        // once https://github.com/scala/scala3/issues/20073 is resolved and new scala version is published
        fqn != "scala.AnyKind"
    }
    scala3SyntheticClassesWithExpectedSources.foreach(assertNavigationElementPointsToSources)
  }

  //TODO: fix the implementation when https://github.com/scala/scala3/issues/20073 is resolved and new scala version is published
  def testThatNavigationElementPointsToSourceElement_SyntheticAliases(): Unit = {
    return
    val syntheticObjects = SyntheticClasses.get(getProject).aliases
    syntheticObjects.foreach(assertNavigationElementPointsToSources)
  }
}

object ScalaSyntheticClassesNavigationalElementTest {
  /**
   * "override" default scala sdk loader, use non-standard resolvers
   * TODO: remove these workarounds once 2.13.14 is resolved AND latest Scala 3.X depends on it
   */
  def libraryLoadersWithSeparateScalaLibraries(superLibraryLoaders: Seq[LibraryLoader]): Seq[LibraryLoader] = {
    val dependencyManager = new DependencyManagerBase {
      override protected def resolvers: Seq[Resolver] = super.resolvers ++ Seq(Resolver.TypesafeScalaPRValidationSnapshots)
    }
    val scala2LibraryLoader = ScalaLibraryLoader(ScalaVersion.Latest.Scala_2_13_RC, dependencyManager)
    val scala3LibraryLoader = ScalaLibraryLoader(ScalaVersion.Latest.Scala_3)

    //We use resolveScalaLibraryTransitiveDependencies = false in order to use the latest 2.13.14 RC version
    val scala3SdkLoader = ScalaSDKLoader(includeLibraryFilesInSdk = false)

    Seq(
      scala3LibraryLoader,
      scala2LibraryLoader,
      scala3SdkLoader
    ) ++ superLibraryLoaders.filterNot(_.is[ScalaSDKLoader])
  }
}