package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.MockPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

abstract class CachedWithRecursionGuardTestBase extends ScalaFixtureTestCase {
  class CachedMockPsiElement extends MockPsiElement(getProject) {
    override def getProject: Project = myFixture.getProject

    override def getParent: PsiElement = null
  }
}
