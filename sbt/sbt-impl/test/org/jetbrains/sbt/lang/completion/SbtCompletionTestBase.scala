package org.jetbrains.sbt.lang.completion

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.sbt.language.SbtFileType

abstract class SbtCompletionTestBase extends ScalaCompletionTestBase {

  override final protected def configureFromFileText(fileText: String): PsiFile =
    configureFromFileText(SbtFileType, fileText)
}
