package org.jetbrains.plugins.scala
package project.converter

import com.intellij.conversion.{ConversionContext, ConverterProvider}

class ScalaProjectConverterProvider extends ConverterProvider {
  override def getConversionDescription = ScalaBundle.message("scala.facets.will.be.converted.to.scala.sdks")

  override def createConverter(context: ConversionContext) = new ScalaProjectConverter(context)
}
