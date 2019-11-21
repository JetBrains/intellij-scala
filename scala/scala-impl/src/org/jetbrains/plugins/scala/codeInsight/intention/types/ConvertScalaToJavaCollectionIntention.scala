package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Converts expression representing scala collection to
 * java equivalent using [[scala.collection.JavaConverters]]
 *
 * @author Eugene Platonov
 *         23/07/13
 */
class ConvertScalaToJavaCollectionIntention extends BaseJavaConvertersIntention {

  val targetCollections = Set(
    "scala.collection.Seq",
    "scala.collection.Set",
    "scala.collection.Map",
    "scala.collection.Iterator",
    "scala.collection.Iterable"
  )

  val alreadyConvertedPrefixes: Set[String] = Set("java.")

  override def getText: String = ScalaBundle.message("convert.scala.to.java.collection.hint")

  def getFamilyName: String = ConvertScalaToJavaCollectionIntention.getFamilyName

  override val importPath: String = "scala.collection.JavaConverters._"

  override val maybeReplaceAsMethod: Option[String] = Some("asJava")
}

object ConvertScalaToJavaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("convert.scala.to.java.collection.name")
}
