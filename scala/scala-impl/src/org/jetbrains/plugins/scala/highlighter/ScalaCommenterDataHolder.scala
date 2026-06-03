package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile

private[highlighter]
class ScalaCommenterDataHolder(file: PsiFile) extends CommenterDataHolder {
  putUserData(ScalaCommenterDataHolder.PSI_FILE_KEY, file)

  def getPsiFile: PsiFile = getUserData(ScalaCommenterDataHolder.PSI_FILE_KEY)
}

private[highlighter]
object ScalaCommenterDataHolder {
  private val PSI_FILE_KEY = Key.create[PsiFile]("SCALA_COMMENTER_PSI_FILE")
}