package org.jetbrains.plugins.scala.lang.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction.findAllTargetElements
import com.intellij.psi.PsiElement
import org.junit.Assert.{assertEquals, assertTrue}

abstract class GoToDeclarationTestBase extends GoToTestBase {
  protected def doTest(fileText: String, expected: (PsiElement => Boolean, String)*): Unit = {
    configureFromFileText(fileText)

    val editor = getEditor
    val targets =
      findAllTargetElements(getProject, editor, editor.getCaretModel.getOffset)
        .map(_.getNavigationElement)
        .toSet

    checkTargets(targets, expected)
  }

  protected def checkTargets(targets: Iterable[PsiElement], expected: Seq[(PsiElement => Boolean, String)]): Unit = {
    assertEquals("Wrong number of targets: ", expected.size, targets.size)

    val wrongTargets = for {
      (actualElement, (predicate, expectedName)) <- targets.zip(expected)
      actualName = this.actualName(actualElement)

      if !predicate(actualElement) || actualName != expectedName
    } yield actualElement -> actualName

    assertTrue("Wrong targets: " + wrongTargets, wrongTargets.isEmpty)
  }
}
