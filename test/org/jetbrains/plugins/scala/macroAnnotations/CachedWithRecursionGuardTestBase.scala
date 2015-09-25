package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.MockPsiElement
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInfixExprImpl

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/24/15.
 */
class CachedWithRecursionGuardTestBase extends ScalaFixtureTestCase {
  class CachedMockPsiElement extends MockPsiElement(getProject) {
    override def getProject = myFixture.getProject

    override def getParent = null
  }
}

object CachedWithRecursionGuardTestBase {
  val FAKE_KEY: MappedKey[(Option[Int], Int), String] = Key.create("fake.key")
}
