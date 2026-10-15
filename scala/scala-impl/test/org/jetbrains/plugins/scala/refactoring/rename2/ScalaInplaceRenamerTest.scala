package org.jetbrains.plugins.scala.refactoring.rename2

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaInplaceTypeAliasIntroducer
import org.jetbrains.plugins.scala.lang.refactoring.rename.inplace.{ScalaLocalInplaceRenamer, ScalaMemberInplaceRenamer}
import org.junit.Assert.{assertNotNull, assertTrue}

class ScalaInplaceRenamerTest extends ScalaFixtureTestCase {
  override protected def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable)
  }

  def testLocal(): Unit = checkRename(
    s"object Test { def f(): Int = { val ${CARET}value = 1; value } }",
    "object Test { def f(): Int = { val renamed = 1; renamed } }",
    (element, editor) => new ScalaLocalInplaceRenamer(element, editor)
  )

  def testMember(): Unit = checkRename(
    s"class Test { val ${CARET}value = 1; def f(): Int = value }",
    "class Test { val renamed = 1; def f(): Int = renamed }",
    (element, editor) => new ScalaMemberInplaceRenamer(element, element, editor)
  )

  def testTypeAlias(): Unit = checkRename(
    s"class Test { type ${CARET}Value = String; val value: Value = \"\" }",
    "class Test { type renamed = String; val value: renamed = \"\" }",
    (element, editor) => new ScalaInplaceTypeAliasIntroducer(element.asInstanceOf[ScNamedElement], editor)
  )

  private def checkRename(before: String, after: String,
                          createRenamer: (PsiNamedElement, Editor) => VariableInplaceRenamer): Unit = {
    myFixture.configureByText("dummy.scala", before)
    val editor = myFixture.getEditor
    val element = myFixture.getElementAtCaret.asInstanceOf[PsiNamedElement]
    val renamer = createRenamer(element, editor)
    try {
      executeWriteActionCommand("Rename") {
        assertTrue("In-place rename should start", renamer.performInplaceRename())
      }
      val template = TemplateManagerImpl.getTemplateState(editor)
      assertNotNull("In-place rename should create a live template", template)
      myFixture.`type`("renamed")
      executeWriteActionCommand("Finish rename") {
        template.gotoEnd(false)
      }
      myFixture.checkResult(after, true)
    } finally {
      renamer.finish(true)
    }
  }
}
