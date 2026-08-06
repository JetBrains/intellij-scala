package org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.ScalaDocRefLinkLanguage

class ScalaDocRefLinkFile(viewProvider: FileViewProvider)
  extends PsiFileBase(viewProvider, ScalaDocRefLinkLanguage.INSTANCE) {

  override def getFileType: FileType = ScalaFileType.INSTANCE

  override def toString: String = "ScalaDocRefLinkFile"
}
