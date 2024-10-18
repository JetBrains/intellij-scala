package org.jetbrains.plugins.scala.compiler

import com.intellij.compiler.{CompilerDirectHierarchyInfo, CompilerReferenceService}
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.vfs.{VfsUtil, VirtualFileUtil}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertNotNull, assertNull}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11,
  TestJdkVersion.JDK_17
))
@Category(Array(classOf[CompilationTests]))
abstract class JavaCompilerReferencesSearchTestBase(
  override protected val incrementalityType: IncrementalityType
) extends ScalaCompilerTestBase {

  def testCompilerReferencesSearch(): Unit = {
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_1_8)

    addFileToProjectSources("Greeter.java",
      """public interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    addFileToProjectSources("FooGreeter.java",
      """public class FooGreeter implements Greeter {
        |  @Override
        |  public String greeting() {
        |    return "Foo";
        |  }
        |}
        |""".stripMargin)
    addFileToProjectSources("BarGreeter.java",
      """public class BarGreeter implements Greeter {
        |  @Override
        |  public String greeting() {
        |    return "Bar";
        |  }
        |}
        |""".stripMargin)

    val compilerReferenceService = CompilerReferenceService.getInstance(myProject)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)

    val greeterPsiClass =
      Option(VfsUtil.findFile(getSourceRootDir.toNioPath.resolve("Greeter.java"), true))
        .flatMap(source => Option(VirtualFileUtil.findPsiFile(source, myProject)))
        .flatMap(psiFile => Option(PsiTreeUtil.findChildOfType(psiFile, classOf[PsiClass])))
        .orNull

    assertNotNull("Could not find the 'Greeter' PsiClass", greeterPsiClass)

    val info = compilerReferenceService.getDirectInheritors(
      greeterPsiClass,
      greeterPsiClass.getUseScope.asInstanceOf[GlobalSearchScope],
      JavaFileType.INSTANCE
    )

    assertCompilerDirectHierarchyInfo(info)
  }

  protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit
}

class JavaCompilerReferencesSearchTest_IDEA extends JavaCompilerReferencesSearchTestBase(IncrementalityType.IDEA) {
  override protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit = {
    val references = info.getHierarchyChildren.toList
    assertEquals(2, references.size())
  }
}

class JavaCompilerReferencesSearchTest_Zinc extends JavaCompilerReferencesSearchTestBase(IncrementalityType.SBT) {
  override protected def assertCompilerDirectHierarchyInfo(info: CompilerDirectHierarchyInfo): Unit = {
    // The hierarchy info returned by the compiler reference service should be null when the indices are disabled for
    // a project.
    assertNull(info)
    // At the moment, because we cannot generate Java compiler references using Zinc, we fall back to PSI search.
    // In the future, when SCL-21719 is implemented, this test is supposed to fail and be rewritten with different
    // expectations, similar (or identical) to the IDEA test above.
  }
}
