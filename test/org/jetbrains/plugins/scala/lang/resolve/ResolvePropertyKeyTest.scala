package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.lang.properties.IProperty
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.junit.Assert

/**
 * Nikolay.Tropin
 * 2014-09-26
 */
class ResolvePropertyKeyTest extends ScalaResolveTestCase {
  override def folderPath: String = {
    super.folderPath + "resolve/propertyKey/"
  }

  protected override def rootPath: String = {
    super.folderPath + "resolve/propertyKey/"
  }

  def testMain() {
    val settings = ScalaProjectSettings.getInstance(getProjectAdapter)
    val oldI18n = settings.isDisableI18N
    settings.setDisableI18N(false)

    val ref: PsiReference = findReferenceAtCaret
    val psiElement: PsiElement = ref.resolve
    Assert.assertTrue(psiElement.isInstanceOf[IProperty])

    settings.setDisableI18N(oldI18n)
  }
}
