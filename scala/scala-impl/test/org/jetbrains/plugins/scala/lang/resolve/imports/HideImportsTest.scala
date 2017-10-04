package org.jetbrains.plugins.scala
package lang
package resolve
package imports


import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.junit.Assert

class HideImportsTest extends ScalaResolveTestCase {
  override def folderPath: String = super.folderPath() + "resolve/imports/simple/"

  def testHidePredefImplicit() {
    findReferenceAtCaret() match {
      case r: PsiPolyVariantReference =>
        val results = r.multiResolve(false)

        assert(results.length == 1)
        val res = results(0)
        assert(res.isInstanceOf[ScalaResolveResult])
        val srr = res.asInstanceOf[ScalaResolveResult]
        Assert.assertEquals("caPitalize", srr.element.name)
      case _ => throw new Exception("Wrong reference!")
    }
  }
}