package org.jetbrains.sbt
package codeInsight.template

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/**
 * @author Nikolay Obedin
 * @since 7/31/14.
 */
class SbtDefaultLiveTemplatesProvider extends DefaultLiveTemplatesProvider {
  def getDefaultLiveTemplateFiles: Array[String] = Array[String]("/liveTemplates/sbt")

  def getHiddenLiveTemplateFiles: Array[String] = Array.empty
}

