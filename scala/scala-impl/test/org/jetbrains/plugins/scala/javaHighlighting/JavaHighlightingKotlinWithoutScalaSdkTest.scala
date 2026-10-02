package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.psi.{PsiFile, PsiNamedElement}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.{IndexingTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClassElementFinder
import org.jetbrains.plugins.scala.project.ModuleExt
import org.junit.Assert

class JavaHighlightingKotlinWithoutScalaSdkTest extends JavaHighlightingTestBase {

  override protected def scalaSdkAndLibraryLoaders: Seq[LibraryLoader] = Seq.empty

  private val scalaSdkLoader = ScalaSDKLoader()
  private var scalaModule: Module = scala.compiletime.uninitialized

  override protected def setUp(): Unit = {
    super.setUp()

    scalaModule = PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[? <: ModuleBuilder]],
      "scalaModule",
      myFixture.getTempDirFixture.findOrCreateDir("scalaModule")
    )
    PsiTestUtil.addSourceRoot(scalaModule, myFixture.getTempDirFixture.findOrCreateDir("scalaModule/src"))
    scalaSdkLoader.init(using scalaModule, version)
    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
  }

  override def tearDown(): Unit = {
    try scalaSdkLoader.clean(using scalaModule)
    finally super.tearDown()
  }

  def testIntInScalaPackageWithScalaSdkInAnotherModule(): Unit = {
    Assert.assertTrue(
      "The regression test requires a module without a Scala SDK",
      myModule.scalaSdk.isEmpty
    )
    Assert.assertTrue(
      "The regression test requires another module with a Scala SDK",
      scalaModule.scalaSdk.nonEmpty
    )

    val errors = errorsFromKotlinCode(
      """package scala
        |
        |class KotlinAndScala {
        |  val x: Int = 42
        |}
        |""".stripMargin
    )

    assertKotlinIntReferenceResolvesToKotlinInt(myFixture.getFile)
    assertMessagesTextImpl("", errors)
  }

  def testSyntheticClassesAreVisibleOnlyInScalaModuleScope(): Unit = {
    val finder = new SyntheticClassElementFinder(getProject)
    val kotlinModuleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)
    val scalaModuleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(scalaModule)

    Assert.assertNull(finder.findClass("scala.Int", kotlinModuleScope))
    Assert.assertTrue(finder.findClasses("scala.Int", kotlinModuleScope).isEmpty)

    val scalaInt = finder.findClass("scala.Int", scalaModuleScope)
    Assert.assertNotNull(scalaInt)
    Assert.assertEquals("scala.Int", scalaInt.getQualifiedName)
    Assert.assertTrue(finder.findClasses("scala.Int", scalaModuleScope).nonEmpty)
  }

  private def assertKotlinIntReferenceResolvesToKotlinInt(file: PsiFile): Unit = {
    val intOffset = file.getText.indexOf("Int")
    Assert.assertTrue("The regression test source must contain Int", intOffset >= 0)

    val reference = file.findReferenceAt(intOffset)
    Assert.assertNotNull("Kotlin Int must have a PSI reference", reference)

    reference.resolve() match {
      case named: PsiNamedElement =>
        Assert.assertEquals("Kotlin Int must resolve to Int", "Int", named.getName)
        Assert.assertTrue(
          s"Kotlin Int must resolve to Kotlin PSI, but was ${named.getClass.getName}",
          named.getClass.getName.startsWith("org.jetbrains.kotlin.psi.")
        )
      case other =>
        Assert.fail(s"Kotlin Int must resolve to a named element, but was $other")
    }
  }
}
