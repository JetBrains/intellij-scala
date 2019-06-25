package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor

/**
 * @author ilyas
 */
class ResolvePackagingsTest extends ScalaResolveTestCase {
  import junit.framework.TestCase._

  override def folderPath: String = super.folderPath + "resolve/packages/solid/my/scala/stuff/"

  override protected def sourceRootPath: String = super.folderPath + "resolve/packages/"

  def testMain(): Unit = {
    val ref = findReferenceAtCaret()
    val psiElement = ref.resolve
    assertTrue(psiElement.isInstanceOf[ScPrimaryConstructor])
  }
}