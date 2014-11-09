package org.jetbrains.plugins.scala
package codeInsight.template.impl

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

class ScalaDefaultLiveTemplatesProvider extends DefaultLiveTemplatesProvider {
  def getDefaultLiveTemplateFiles: Array[String] = Array[String]("/liveTemplates/scala")

  def getHiddenLiveTemplateFiles: Array[String] = Array.empty
}