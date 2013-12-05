package org.jetbrains.plugins.scala
package decompiler

import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StubVersion

/**
 * @author Alefas
 * @since 05.12.13
 */
class ScalaClassFileStubBuilder extends ClassFileStubBuilder {
  //todo: this is the hack
  override def getStubVersion: Int = StubVersion.STUB_VERSION
}
