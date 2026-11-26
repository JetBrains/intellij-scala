package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.junit.Assert._

class ScFunctionReturnUsagesTest extends SimpleTestCase {
  def testTailExpressionEdit(): Unit = {
    val file = fixture.configureByText("test.scala", "def f(): Unit = { 1 }")
    val function = findFunction(file)
    val before = function.returnUsages
    edit(fixture.getEditor.getDocument, "1", "2")
    assertSame(function, findFunction(file))
    val after = function.returnUsages
    assertNotSame(before, after)
    assertEquals(Set("2"), after.map(_.getText))
    assertTrue(after.forall(_.isValid))
  }

  def testAddingAndRemovingReturnInLoop(): Unit = {
    val file = fixture.configureByText("test.scala", "def f(): Unit = { while (true) { () }; () }")
    val function = findFunction(file)
    assertEquals(Set("()"), function.returnUsages.map(_.getText))
    val document = fixture.getEditor.getDocument

    edit(document, "{ () }", "{ return () }")
    assertSame(function, findFunction(file))
    assertEquals(Set("return ()", "()"), function.returnUsages.map(_.getText))

    edit(document, "return ()", "()")
    assertSame(function, findFunction(file))
    val after = function.returnUsages
    assertEquals(Set("()"), after.map(_.getText))
    assertTrue(after.forall(_.isValid))
  }

  def testSiblingFunctionEditKeepsCache(): Unit = {
    val file = fixture.configureByText("test.scala", "object Test { def f(): Unit = { 1 }; def g(): Unit = { 2 } }")
    assertTrue(file.isPhysical)
    val function = findFunction(file)
    val usages = function.returnUsages
    edit(fixture.getEditor.getDocument, "2", "3")
    assertSame(function, findFunction(file))
    assertSame(usages, function.returnUsages)
  }

  def testOtherFileEditKeepsCache(): Unit = {
    val file = fixture.addFileToProject("First.scala", "object First { def f(): Unit = { 1 } }")
    val function = findFunction(file)
    fixture.configureByText("Other.scala", "object Other { val x = 2 }")
    val usages = function.returnUsages
    edit(fixture.getEditor.getDocument, "2", "3")
    assertSame(usages, function.returnUsages)
  }

  def testNonPhysicalBodyEdit(): Unit = {
    val function = "def f(): Unit = { 1 }".parse[ScFunctionDefinition]
    assertFalse(function.isPhysical)
    val before = function.returnUsages
    inWriteAction {
      function.body.get.replace(ScalaPsiElementFactory.createExpressionFromText("{ return (); 2 }", scalaCodeParsingFeatures))
    }
    val after = function.returnUsages
    assertNotSame(before, after)
    assertEquals(Set("return ()", "2"), after.map(_.getText))
    assertTrue(after.forall(_.isValid))
  }

  def testCopyUsesItsOwnExpressions(): Unit = {
    val function = "def f(): Unit = { return (); 1 }".parse[ScFunctionDefinition]
    val originalUsages = function.returnUsages
    val copy = function.copy().asInstanceOf[ScFunctionDefinition]
    val copyUsages = copy.returnUsages
    assertEquals(originalUsages.map(_.getText), copyUsages.map(_.getText))
    assertTrue(originalUsages.intersect(copyUsages).isEmpty)
    assertTrue(copyUsages.forall(_.parentOfType[ScFunctionDefinition].contains(copy)))
  }

  private def findFunction(file: PsiFile): ScFunctionDefinition =
    file.depthFirst().findByType[ScFunctionDefinition].get

  private def edit(document: Document, oldText: String, newText: String): Unit = {
    val offset = document.getText.indexOf(oldText)
    assertTrue(offset >= 0)
    inWriteCommandAction {
      document.replaceString(offset, offset + oldText.length, newText)
      PsiDocumentManager.getInstance(fixture.getProject).commitDocument(document)
    }(using fixture.getProject)
  }
}
