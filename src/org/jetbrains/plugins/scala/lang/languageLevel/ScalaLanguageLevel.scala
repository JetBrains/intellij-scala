package org.jetbrains.plugins.scala
package lang.languageLevel

/**
 * @author Alefas
 * @since 24.10.12
 */
object ScalaLanguageLevel extends Enumeration {
  type ScalaLanguageLevel = ScalaLanguageLevel.Value

  val SCALA2_9 = Value("Scala 2.9")
  val SCALA2_10 = Value("Scala 2.10")
  val SCALA2_10_VIRTUALIZED = Value("Scala 2.10 virtualized")

  val DEFAULT_LANGUAGE_LEVEL = SCALA2_10

  def isVirtualized(languageLevel: ScalaLanguageLevel): Boolean = {
    languageLevel match {
      case SCALA2_10_VIRTUALIZED => true
      case _ => false
    }
  }

  def isThoughScala2_10(languageLevel: ScalaLanguageLevel): Boolean = {
    languageLevel match {
      case SCALA2_10 | SCALA2_10_VIRTUALIZED => true
      case _ => false
    }
  }

  def valuesArray(): Array[ScalaLanguageLevel] = values.toArray

  implicit class ValueImpl(val languageLevel: ScalaLanguageLevel) extends AnyVal {
    def isVirtualized: Boolean = {
      languageLevel match {
        case SCALA2_10_VIRTUALIZED => true
        case _ => false
      }
    }

    def isThoughScala2_10: Boolean = {
      languageLevel match {
        case SCALA2_10 | SCALA2_10_VIRTUALIZED => true
        case _ => false
      }
    }
  }
}
