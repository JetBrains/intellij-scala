package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.junit.Assert._

import java.nio.file.Path

class ConstructorFromJavaResolveTest extends ScalaResolveTestCase {

  override def folderPath: Path = super.folderPath / "resolve" / "constructorFromJava"

  override def sourceRootPath: Path = folderPath

  def testScl8083(): Unit = {
    findReferenceAtCaret() match {
      case st: ScStableCodeReference =>
        val variants = st.resolveAllConstructors
        assertTrue("Single resolve expected", variants.length == 1)
    }
  }
}
