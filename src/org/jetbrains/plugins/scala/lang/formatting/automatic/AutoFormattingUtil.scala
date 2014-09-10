package org.jetbrains.plugins.scala
package lang.formatting.automatic

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.formatting.Indent
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.openapi.application.ApplicationManager

/**
 * Created by Roman.Shein on 31.07.2014.
 */
object AutoFormattingUtil {
  def getRoot(code: String, psiManager: PsiManager, codeStyleSettings: CodeStyleSettings = new CodeStyleSettings) = {
    val astNode = ScalaPsiElementFactory.createScalaFile(code, psiManager).getNode
    new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
  }

  def wrapInReadAction(a: => Unit) = {
    ApplicationManager.getApplication.runReadAction(new Runnable {
      override def run(): Unit = {
        a
      }
    })
  }
}
