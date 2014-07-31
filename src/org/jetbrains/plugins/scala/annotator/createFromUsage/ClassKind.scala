package org.jetbrains.plugins.scala
package annotator.createFromUsage

import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
sealed abstract class ClassKind(val keyword: String, val templateName: String)

object Trait extends ClassKind("trait", ScalaFileTemplateUtil.SCALA_TRAIT)
object Object extends ClassKind("object", ScalaFileTemplateUtil.SCALA_OBJECT)
object Class extends ClassKind("class", ScalaFileTemplateUtil.SCALA_CLASS)
