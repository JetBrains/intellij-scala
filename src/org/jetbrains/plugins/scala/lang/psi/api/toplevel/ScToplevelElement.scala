package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScToplevelElement extends ScalaPsiElement {
  def typeDefinitions(): Array[ScTypeDefinition] = {
    val buff = new ArrayBuffer[ScTypeDefinition]
    for (clazz <- immediateTypeDefinitions) buff += clazz
    for (pack <- packagings) buff ++= pack.typeDefinitions
    buff.toArray
  }

  def immediateTypeDefinitions = findChildrenByClass(classOf[ScTypeDefinition])

  def packagings = findChildrenByClass(classOf[ScPackaging])
}