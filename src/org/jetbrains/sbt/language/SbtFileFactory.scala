package org.jetbrains.sbt
package language

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.parser.ScalaFileFactory

/**
 * @author Pavel Fatin
 */
class SbtFileFactory extends ScalaFileFactory {
  def createFile(provider: FileViewProvider): Option[SbtFileImpl] = {
    Option(provider.getVirtualFile.getFileType) collect {
      case SbtFileType => new SbtFileImpl(provider)
    }
  }
}
