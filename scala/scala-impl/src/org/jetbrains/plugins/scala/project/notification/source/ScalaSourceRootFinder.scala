package org.jetbrains.plugins.scala
package project.notification.source

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector
import com.intellij.util.NullableFunction

/**
 * @author Alexander Podkhalyuzin
 */
class ScalaSourceRootFinder extends JavaSourceRootDetector {
  def getLanguageName: String = "Scala"

  def getFileExtension: String = "scala"

  def getPackageNameFetcher: NullableFunction[CharSequence, String] = {
    new NullableFunction[CharSequence, String] {
      def fun(dom: CharSequence): String = {
        ScalaDirUtil.getPackageStatement(dom)
      }
    }
  }
}