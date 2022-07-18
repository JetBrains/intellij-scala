package org.jetbrains.plugins.scala
package lang.psi.scope

import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.experimental.categories.Category

import scala.reflect.ClassTag

@Category(Array(classOf[LanguageTests]))
class PackagePrivateUseScopeTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override def getTestDataPath = super.getTestDataPath + "/useScope/privatePackage"

  private def doTestPackagePrivateDefinition[Named <: PsiNamedElement : ClassTag](elementName: String): Unit = {
    val currentFile  = myFixture.configureByFile("foo/Definitions.scala")
    val samePackage  = myFixture.configureByFile("foo/SamePackage.scala")
    val innerPackage = myFixture.configureByFile("foo/bar/InnerPackage.scala")
    val otherPackage = myFixture.configureByFile("baz/OtherPackage.scala")

    val definition = findByName[Named](currentFile, elementName)
    assertScopeContains(definition, currentFile, samePackage, innerPackage)
    assertScopeNotContain(definition, otherPackage)
  }

  private def checkEscapePackagePrivateScope[Named <: PsiNamedElement : ClassTag](elementName: String): Unit = {
    val currentFile  = myFixture.configureByFile("foo/Definitions.scala")
    val otherPackage = myFixture.configureByFile("baz/OtherPackage.scala")

    val definition = findByName[Named](currentFile, elementName)
    assertScopeContains(definition, currentFile, otherPackage)
  }

  def testPrivateTopLevelClass(): Unit =
    doTestPackagePrivateDefinition[ScClass]("PrivateTopLevel")

  def testPrivateTopLevelObject(): Unit =
    doTestPackagePrivateDefinition[ScObject]("PrivateTopLevel")

  def testPackagePrivateTopLevelClass(): Unit =
    doTestPackagePrivateDefinition[ScClass]("PackagePrivateTopLevel")

  def testPublicClassInPrivateClass(): Unit =
    doTestPackagePrivateDefinition[ScClass]("PublicInPrivate")

  def testPackagePrivateMethod(): Unit =
    doTestPackagePrivateDefinition[ScFunctionDefinition]("packagePrivateMethod")

  def testPackagePrivateInnerClass(): Unit =
    doTestPackagePrivateDefinition[ScClass]("PackagePrivateClass")

  def testPackagePrivateTypeAlias(): Unit =
    doTestPackagePrivateDefinition[ScTypeAlias]("PackagePrivateTypeAlias")

  def testPrivateClassParameter(): Unit =
    doTestPackagePrivateDefinition[ScClassParameter]("privateClassParam")

  def testInnerClassParameter(): Unit =
    doTestPackagePrivateDefinition[ScClassParameter]("innerClassParam")

  //may escape via inheritors
  def testPackagePrivateConstructorParameter(): Unit =
    checkEscapePackagePrivateScope[ScClassParameter]("packagePrivateCtorParameter")

  //may escape via inheritors
  def testPublicMemberOfPackagePrivateClass(): Unit =
    checkEscapePackagePrivateScope[ScFunctionDefinition]("publicMember")

  //may escape via inheritors
  def testPackagePrivateClassParameter(): Unit =
    checkEscapePackagePrivateScope[ScClassParameter]("packagePrivateClassParam")

  //escapes as names argument
  def testCtorPackagePrivateParameter(): Unit =
    checkEscapePackagePrivateScope[ScClassParameter]("ctorPrivateParameter")

  private def findByName[Named <: PsiNamedElement : ClassTag](file: PsiFile, name: String): Named = {
    file.depthFirst().collectFirst {
      case p: Named if p.name == name => p
    }.get
  }

  private def assertScopeContains(element: PsiElement, files: PsiFile*): Unit = {
    for (file <- files) {
      assertTrue(s"Scope of $element should contain ${file.getName}", scopeContains(element, file))
    }
  }

  private def assertScopeNotContain(element: PsiElement, file: PsiFile): Unit = {
    assertFalse(s"Scope of $element should not contain ${file.getName}", scopeContains(element, file))
  }

  private def scopeContains(element: PsiElement, file: PsiFile) =
    element.getUseScope.contains(file.getVirtualFile)
}
