package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => Caret}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.navigation.SuperMemberGutterNavigationFixture
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class GutterMarkersTest_Scala3 extends GutterMarkersTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  // NOTE: Scala 3 forbids an export alias from overriding a concrete base member.
  // Keep this invalid code covered optimistically until the Scala annotator reports that error.
  def testExportedMemberOverridesConcreteBaseMemberOptimistically(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def run(): Unit = ()
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.run$Caret
       |}
       |""".stripMargin,
    "Overrides member from", refToElement("Base", "run", refText = "Base")
  )

  def testExportedMemberImplementsAbstractBaseMember(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def run(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.run$Caret
       |}
       |""".stripMargin,
    "Implements member from", refToElement("Base", "run", refText = "Base")
  )

  def testBracedExportedMemberImplementsAbstractBaseMember(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def run(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.{run$Caret}
       |}
       |""".stripMargin,
    "Implements member from", refToElement("Base", "run", refText = "Base")
  )

  def testOverloadedExportedMemberImplementsAbstractBaseMember(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def run(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.run$Caret
       |}
       |""".stripMargin,
    "Implements member from", refToElement("Base", "run", refText = "Base")
  )

  def testRenamedExportedMemberOverridesConcreteBaseMemberOptimistically(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def execute(): Unit = ()
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.{run => execute$Caret}
       |}
       |""".stripMargin,
    "Overrides member from", refToElement("Base", "execute", refText = "Base")
  )

  def testRenamedExportedMemberImplementsAbstractBaseMember(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def execute(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.{run => execute$Caret}
       |}
       |""".stripMargin,
    "Implements member from", refToElement("Base", "execute", refText = "Base")
  )

  def testRenamedOverloadedExportedMemberImplementsAbstractBaseMember(): Unit = doTestSingleTooltipAtCaret(
    s"""trait Base {
       |  def execute(): Unit
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.{run => execute$Caret}
       |}
       |""".stripMargin,
    "Implements member from", refToElement("Base", "execute", refText = "Base")
  )

  def testExportedMemberWithDifferentSignatureDoesNotOverride(): Unit = doTestNoLineMarkersAtCaret(
    s"""trait Base {
       |  def run(): Unit = ()
       |}
       |
       |trait Mixin {
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.run$Caret
       |}
       |""".stripMargin
  )

  def testBaseMemberWithDifferentSignatureDoesNotHaveExportedImplementation(): Unit = doTestNoLineMarkersAtCaret(
    s"""trait Base {
       |  def run$Caret(): Unit = ()
       |}
       |
       |trait Mixin {
       |  def run(value: Int): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.run
       |}
       |""".stripMargin
  )

  def testExportedMemberOverrideGutterNavigatesToSuperMember(): Unit = {
    val target = navigateToSuperMember(s"""
         |trait Base {
         |  def run(): Unit = ()
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class Exported extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.run$Caret
         |}
         |""".stripMargin
    )
    assertEquals("run", target.getName)
  }

  def testRenamedExportedMemberOverrideGutterNavigatesToSuperMember(): Unit = {
    val target = navigateToSuperMember(s"""
         |trait Base {
         |  def execute(): Unit = ()
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class Exported extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.{run => execute$Caret}
         |}
         |""".stripMargin
    )
    assertEquals("execute", target.getName)
  }

  def testExportedMemberImplementationGutterNavigatesToAbstractSuperMember(): Unit = {
    val target = navigateToSuperMember(s"""
         |trait Base {
         |  def run(): Unit
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class Exported extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.run$Caret
         |}
         |""".stripMargin
    )
    assertEquals("run", target.getName)
  }

  def testRenamedExportedMemberHasOverridesGutterNavigatesToExport(): Unit = {
    val target = navigateToImplementation(s"""
         |trait Base {
         |  def execute$Caret(): Unit = ()
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class Exported extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.{run => execute}
         |}
         |""".stripMargin
    )
    assertTrue(target.isInstanceOf[ScExportStmt])
    assertEquals("export delegate.{run => execute}", target.getText.trim)
  }

  def testRenamedExportedMemberHasImplementationsGutterNavigatesToExport(): Unit = {
    val target = navigateToImplementation(s"""
         |trait Base {
         |  def execute$Caret(): Unit
         |}
         |
         |trait Mixin {
         |  def run(): Unit = ()
         |}
         |
         |class Exported extends Base {
         |  val delegate: Mixin = new Mixin {}
         |  export delegate.{run => execute}
         |}
         |""".stripMargin
    )
    assertTrue(target.isInstanceOf[ScExportStmt])
    assertEquals("export delegate.{run => execute}", target.getText.trim)
  }

  private def navigateToSuperMember(@Language("Scala 3") fileText: String): com.intellij.psi.PsiNamedElement = {
    val fileName = "Exported.scala"
    myFixture.configureByText(fileName, fileText)
    myFixture.checkHighlighting()

    new SuperMemberGutterNavigationFixture(myFixture)
      .navigateToSuperMemberTarget(fileName)
      .asInstanceOf[com.intellij.psi.PsiNamedElement]
  }

  private def navigateToImplementation(@Language("Scala 3") fileText: String): com.intellij.psi.PsiElement = {
    val fileName = "Exported.scala"
    myFixture.configureByText(fileName, fileText)
    myFixture.checkHighlighting()

    new SuperMemberGutterNavigationFixture(myFixture)
      .navigateToImplementationTarget(fileName)
  }

  def testWildcardExportDoesNotCreateOverrideGutter(): Unit = doTestNoLineMarkersAtCaret(
    s"""trait Base {
       |  def run(): Unit = ()
       |}
       |
       |trait Mixin {
       |  def run(): Unit = ()
       |}
       |
       |class Exported extends Base {
       |  val delegate: Mixin = new Mixin {}
       |  export delegate.*$Caret
       |}
       |""".stripMargin
  )
}
