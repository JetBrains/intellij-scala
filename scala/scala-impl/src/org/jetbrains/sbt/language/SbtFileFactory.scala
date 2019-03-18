package org.jetbrains.sbt
package language

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.parser.ScalaFileFactory

/**
 * @author Pavel Fatin
 */
final class SbtFileFactory extends ScalaFileFactory {

  def createFile(provider: FileViewProvider): Option[SbtFileImpl] = SbtFileImpl.unapply(provider)
}
