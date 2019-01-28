package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */
final class ScalaDefaultLiveTemplatesProvider extends DefaultLiveTemplatesProvider {

  override def getDefaultLiveTemplateFiles = Array("/liveTemplates/scala")

  override def getHiddenLiveTemplateFiles = Array.empty[String]
}