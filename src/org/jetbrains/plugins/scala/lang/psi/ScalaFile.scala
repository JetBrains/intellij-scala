package org.jetbrains.plugins.scala.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:53:33
 */
class ScalaFile ( viewProvider : FileViewProvider )
  extends PsiFileBase ( viewProvider, ScalaFileType.SCALA_FILE_TYPE.getLanguage() ) {

  def getFileType() = {
    ScalaFileType.SCALA_FILE_TYPE
  }
}
