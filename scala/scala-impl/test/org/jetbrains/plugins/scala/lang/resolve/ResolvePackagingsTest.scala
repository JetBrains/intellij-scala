package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.junit.Assert._

import java.nio.file.Path

class ResolvePackagingsTest extends ScalaResolveTestCase {
  override def folderPath: Path = super.folderPath / "resolve" / "packages" / "solid" / "my" / "scala" / "stuff"

  override protected def sourceRootPath: Path = super.folderPath / "resolve" / "packages"

  def testMain(): Unit = {
    val ref = findReferenceAtCaret()
    val psiElement = ref.resolve
    assertTrue(psiElement.is[ScPrimaryConstructor])
  }
}
