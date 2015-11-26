package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.{MockPsiElement, MockPsiManager}
import com.intellij.openapi
import com.intellij.psi.impl.PsiModificationTrackerImpl
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/18/15.
 */
abstract class CachedTestBase extends ScalaFixtureTestCase {

  val disposable = new openapi.Disposable {
    override def dispose(): Unit = ()
  }

  abstract class Managed extends MockPsiElement(disposable) {
    override val getManager = new MockPsiManager(getProject) {
      override def getModificationTracker: PsiModificationTrackerImpl = {
        super.getModificationTracker.asInstanceOf[PsiModificationTrackerImpl]
      }
    }
  }
}
