package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

class ScalaDefaultLiveTemplatesProvider extends DefaultLiveTemplatesProvider {
  def getDefaultLiveTemplateFiles: Array[String] = Array[String]("/liveTemplates/scala")
}