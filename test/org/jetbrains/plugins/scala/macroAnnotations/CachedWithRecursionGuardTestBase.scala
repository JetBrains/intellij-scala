package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.mock.MockPsiElement
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.caches.CachesUtil._

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
  val FAKE_KEY2: Key[CachedValue[Long]] = Key.create("fake.key.2")
}
