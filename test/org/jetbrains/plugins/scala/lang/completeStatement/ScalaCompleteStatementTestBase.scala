package org.jetbrains.plugins.scala.lang.completeStatement

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * User: Dmitry.Naydanov
 * Date: 27.07.15.
 */
abstract class ScalaCompleteStatementTestBase extends ScalaCodeInsightTestBase {
  protected def getDefaultScalaFileName = "dummy.scala"

  protected def getDefaultJavaFileName = "dummy.java"

  protected def configureAndCheckFile(fileText: String, resultText: String, fileName: String) {
    //We should change this setting in order to be sure EnterProcessor works without 'swap-settings-hack'
    //it was in org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor#moveCaretInsideBracesIfAny
    CodeStyleSettingsManager.getSettings(getProjectAdapter).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true

    configureFromFileTextAdapter(fileName, fileText)
    executeActionAdapter(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
    checkResultByText(StringUtil.convertLineSeparators(resultText))

    CodeStyleSettingsManager.getSettings(getProjectAdapter).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
  }

  def checkScalaFileByText(fileText: String, resultText: String) {
    configureAndCheckFile(fileText, resultText, getDefaultScalaFileName)
  }

  def checkJavaFileByText(fileText: String, resultText: String) {
    configureAndCheckFile(fileText, resultText, getDefaultJavaFileName)
  }
}
