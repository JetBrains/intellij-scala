package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.MockPsiManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
abstract class CachedTestBase extends ScalaFixtureTestCase {

  trait Managed {
    def getProject: Project    = myFixture.getProject
    def getManager: PsiManager = PsiManager.getInstance(getProject)

    def getModTracker: PsiModificationTrackerImpl =
      getManager.getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]
  }
}
