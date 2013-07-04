package org.jetbrains.sbt
package language

import org.jetbrains.plugins.scala.lang.parser.ScalaFileFactory
import com.intellij.psi.FileViewProvider

/**
 * @author Pavel Fatin
 */
class SbtFileFactory extends ScalaFileFactory {
  def createFile(provider: FileViewProvider) = {
    Option(provider.getVirtualFile.getFileType) collect {
      case SbtFileType => new SbtFileImpl(provider)
    }
  }
}
