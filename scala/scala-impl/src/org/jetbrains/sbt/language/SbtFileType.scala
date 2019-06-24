package org.jetbrains.sbt
package language

import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.scala.LanguageFileTypeBase

/**
 * @author Pavel Fatin
 */
//noinspection TypeAnnotation
object SbtFileType extends LanguageFileTypeBase(SbtLanguage.INSTANCE) {
  override def getIcon =
    IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.svg")
}
