package org.jetbrains.plugins.scala
package lang.scaladoc

import lang.completion3.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.actionSystem.{DataContext, IdeActions}

/**
 * User: Dmitry Naydanov
 * Date: 2/6/12
 */

class ScalaDocEnterActionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected def checkGeneratedTextFromString(header: String,  footer: String,  assumedStub: String) {
    checkGeneratedTextFromString(header, footer, assumedStub, a => a)
  }

  protected def checkGeneratedTextFromString(header: String,  footer: String,  assumedStub: String,
                                            transform: String => String) {
    configureFromFileTextAdapter("dummy.scala", header + footer)
    getEditorAdapter.getCaretModel.moveToOffset(header.length - 1)
    val enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)

    enterHandler.execute(getEditorAdapter, new DataContext {
      def getData(dataId: String): AnyRef = {
        dataId match {
          case "Language" | "language" => getFileAdapter.getLanguage
          case "Project" | "project" => getFileAdapter.getProject
          case _ => null
        }
      }
    })

    assert(transform(getFileAdapter.getText).equals(assumedStub))
  }
}