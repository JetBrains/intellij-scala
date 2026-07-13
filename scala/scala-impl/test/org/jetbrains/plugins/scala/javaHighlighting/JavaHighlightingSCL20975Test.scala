package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.tasty.TastyFileType
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

class JavaHighlightingSCL20975Test extends JavaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+
      IvyManagedLoader(("net.sf.ij-plugins" % "ijp-toolkit_3" % "2.3.1").transitive())

  def testJavaClassExtendsClassFromCompiledScala3Library(): Unit = {
    assertNoErrorsInJava(
      """
        |import ij_plugins.toolkit.ui.progress.ProgressReporter4J;
        |
        |public class CounterWithProgress4J extends ProgressReporter4J {}
        |""".stripMargin,
      "CounterWithProgress4J"
    )

    val progressReporter = JavaPsiFacade.getInstance(getProject).findClass(
      "ij_plugins.toolkit.ui.progress.ProgressReporter4J",
      GlobalSearchScope.allScope(getProject)
    )
    assertNotNull("Could not resolve ProgressReporter4J from the compiled Scala 3 library", progressReporter)

    val virtualFile = progressReporter.getContainingFile.getVirtualFile
    assertNotNull("Resolved ProgressReporter4J has no virtual file", virtualFile)
    assertEquals(TastyFileType.getDefaultExtension, virtualFile.getExtension)
    assertTrue(
      "ProgressReporter4J must resolve from library classes",
      ProjectRootManager.getInstance(getProject).getFileIndex.isInLibraryClasses(virtualFile)
    )
  }
}
