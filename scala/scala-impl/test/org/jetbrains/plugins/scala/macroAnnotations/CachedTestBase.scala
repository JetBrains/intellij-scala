package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

abstract class CachedTestBase extends ScalaFixtureTestCase {

  trait Managed {
    def getProject: Project    = myFixture.getProject
    def getManager: PsiManager = PsiManager.getInstance(getProject)
    def getModTracker: ModificationTracker = getManager.getModificationTracker
    def dropCaches(): Unit = getManager.dropPsiCaches()
  }
}
