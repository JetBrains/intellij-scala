package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.{ConversionContext, ConverterProvider}

/**
 * @author Pavel Fatin
 */
class ScalaProjectConverterProvider extends ConverterProvider("scala-facets-to-sdks") {
  def getConversionDescription = "Scala facets will be converted to Scala SDKs"

  def createConverter(context: ConversionContext) = new ScalaProjectConverter(context)
}
