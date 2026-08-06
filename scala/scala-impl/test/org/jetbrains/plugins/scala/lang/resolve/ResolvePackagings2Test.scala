package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.junit.Assert._

import java.nio.file.Path

class ResolvePackagings2Test extends ScalaResolveTestCase {
  override def folderPath: Path = super.folderPath / "resolve" / "packages" / "separated" / "my" / "scala" / "stuff"

  override protected def sourceRootPath: Path = super.folderPath / "resolve" / "packages"

  def testMain(): Unit = {
    val ref = findReferenceAtCaret()
    val psiElement = ref.resolve
    assertTrue(psiElement.is[ScPrimaryConstructor])
    val aClass = psiElement.asInstanceOf[ScPrimaryConstructor]
    assertEquals(aClass.containingClass.qualifiedName, "my.scala.List")
  }
}
