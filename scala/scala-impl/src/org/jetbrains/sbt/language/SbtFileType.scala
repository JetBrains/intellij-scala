package org.jetbrains.sbt
package language

import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.scala.{LanguageFileTypeBase, ScalaLanguage}

/**
 * @author Pavel Fatin
 */
//noinspection TypeAnnotation
object SbtFileType extends LanguageFileTypeBase(ScalaLanguage.INSTANCE) {

  override def getName: String = Sbt.Name

  override def getIcon = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/sbt_file.svg")
}
