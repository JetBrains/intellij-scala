package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.{PsiClass, PsiClassType}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.junit.Assert.{assertEquals, assertNotNull}
import ParentListTypesFixture.ExpectedData

private[typedef] final class ParentListTypesFixture(fixture: CodeInsightTestFixture, testRootDisposable: Disposable) {

  def assertParentListTypes(@Language("Scala 3") scalaText: String, expected: ExpectedData): Unit = {
    val definition = configureAndFindPsiClass("Test.scala", scalaText)
    assertParentListTypes(definition, expected)
  }

  def assertJavaParentListTypes(
    javaFileName: String,
    @Language("JAVA") javaText: String,
    expected: ExpectedData
  ): Unit = {
    val definition = configureAndFindPsiClass(javaFileName, javaText)
    assertParentListTypes(definition, expected)
  }

  def assertParentListTypesWithInjectedSupers(
    @Language("Scala 3") scalaText: String,
    injectedSupers: Seq[String],
    expected: ExpectedData
  ): Unit = {
    val definition = configureAndFindPsiClass("Test.scala", scalaText)
    val typeDefinition = definition match {
      case definition: ScTypeDefinition => definition
      case _ => throw new AssertionError("No Scala type definition found at the caret")
    }
    val injector = new SyntheticMembersInjector {
      override def injectSupers(source: ScTypeDefinition): Seq[String] =
        if (source == typeDefinition) injectedSupers else Seq.empty
    }
    ApplicationManager.getApplication.getExtensionArea
      .getExtensionPoint(SyntheticMembersInjector.EP_NAME)
      .registerExtension(injector, testRootDisposable)

    assertParentListTypes(definition, expected)
  }

  private def configureAndFindPsiClass(fileName: String, sourceText: String): PsiClass = {
    fixture.configureByText(fileName, sourceText)
    findPsiClassAtCaret()
  }

  private def assertParentListTypes(definition: PsiClass, expected: ExpectedData): Unit = {
    assertSupers(definition, expected.expectedGetSupers)
    assertClassTypes("super types", definition, expected.expectedGetSuperTypes, definition.getSuperTypes)
    assertClassTypes("extends", definition, expected.expectedGetExtendsListTypes, definition.getExtendsListTypes)
    assertClassTypes("implements", definition, expected.expectedGetImplementsListTypes, definition.getImplementsListTypes)
  }

  private def findPsiClassAtCaret(): PsiClass = {
    val psiClass = PsiTreeUtil.getParentOfType(fixture.getElementAtCaret, classOf[PsiClass], false)
    assertNotNull("No PSI class found at the caret", psiClass)
    psiClass
  }

  private def assertClassTypes(
    listName: String,
    definition: PsiClass,
    expected: Seq[String],
    actual: Array[PsiClassType]
  ): Unit = {
    val actualCanonicalTexts = actual.map(_.getCanonicalText).toSeq
    assertEquals(
      s"Unexpected $listName list types for `${definition.getText}`",
      expected,
      actualCanonicalTexts
    )
  }

  private def assertSupers(definition: PsiClass, expected: Seq[String]): Unit = {
    val actualQualifiedNames = definition.getSupers.map(_.qualifiedName).toSeq
    assertEquals(
      s"Unexpected supers for `${definition.getText}`",
      expected,
      actualQualifiedNames
    )
  }
}

private[typedef] object ParentListTypesFixture {
  final case class ExpectedData(
    expectedGetSupers: Seq[String],
    expectedGetSuperTypes: Seq[String],
    expectedGetExtendsListTypes: Seq[String],
    expectedGetImplementsListTypes: Seq[String]
  )
}
