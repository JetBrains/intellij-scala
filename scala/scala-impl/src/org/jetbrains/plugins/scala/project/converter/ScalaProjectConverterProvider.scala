package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.{ConversionContext, ConverterProvider}

/**
 * @author Pavel Fatin
 */
class ScalaProjectConverterProvider extends ConverterProvider("scala-facets-to-sdks") {
  override def getConversionDescription = "Scala facets will be converted to Scala SDKs"

  override def createConverter(context: ConversionContext) = new ScalaProjectConverter(context)
}
