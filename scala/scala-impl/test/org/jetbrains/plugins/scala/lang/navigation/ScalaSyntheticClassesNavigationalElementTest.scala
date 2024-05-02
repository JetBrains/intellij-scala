package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.psi.PsiMember
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.junit.Assert.assertTrue

/**
 * See also [[org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProviderTest_SyntheticScalaLibraryElements]]
 */
class ScalaSyntheticClassesNavigationalElementTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  //NOTE: we use custom Scala 2.13 library version because https://github.com/scala/bug/issues/12958 is available only since 2.13.14
  // TODO: we can delete this once latest Scala 3 library depends at least at Scala 2.13.14
  override protected def librariesLoaders: Seq[LibraryLoader] =
    ScalaLibraryLoader.libraryLoadersWithSeparateScalaLibraries(
      super.librariesLoaders,
      ScalaVersion.Latest.Scala_2_13,
      ScalaVersion.Latest.Scala_3
    )

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