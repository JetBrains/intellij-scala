package org.jetbrains.plugins.scala.console

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

import java.lang

object ScalaLanguageConsoleUtils {
  private val SCALA_LANGUAGE_CONSOLE_KEY = new com.intellij.openapi.util.Key[lang.Boolean]("ScalaLanguageConsoleKey")

  def setIsConsole(file: PsiFile, flag: Boolean): Unit = file.putCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY, if (flag) lang.Boolean.TRUE else null)
  def isConsole(file: PsiFile): Boolean = file.getCopyableUserData(SCALA_LANGUAGE_CONSOLE_KEY) == lang.Boolean.TRUE

  def setIsConsole(editor: Editor, flag: Boolean): Unit = editor.putUserData(SCALA_LANGUAGE_CONSOLE_KEY, if (flag) lang.Boolean.TRUE else null)
  def isConsole(editor: Editor): Boolean = editor.getUserData(SCALA_LANGUAGE_CONSOLE_KEY) == lang.Boolean.TRUE
}
