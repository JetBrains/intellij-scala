package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
class ResolvePackagings2Test extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath + "resolve/packages/separated/my/scala/stuff/"

  override protected def sourceRootPath: String = super.folderPath + "resolve/packages/"

  def testMain(): Unit = {
    val ref = findReferenceAtCaret()
    val psiElement = ref.resolve
    assertTrue(psiElement.isInstanceOf[ScPrimaryConstructor])
    val aClass = psiElement.asInstanceOf[ScPrimaryConstructor]
    assertEquals(aClass.containingClass.qualifiedName, "my.scala.List")
  }
}