package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.junit.Assert._

/**
 * @author ilyas
 */
class ResolvePackagingsTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath + "resolve/packages/solid/my/scala/stuff/"

  override protected def sourceRootPath: String = super.folderPath + "resolve/packages/"

  def testMain(): Unit = {
    val ref = findReferenceAtCaret()
    val psiElement = ref.resolve
    assertTrue(psiElement.isInstanceOf[ScPrimaryConstructor])
  }
}