package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.ScalaBundle

/**
  * Motiejus.Juodelis, 18.11.2019
  */
class ImplicitConvertScalaToJavaCollectionIntention extends BaseJavaConvertersIntention {

  override val targetCollections = Set(
    "scala.collection.Seq",
    "scala.collection.Set",
    "scala.collection.Map",
    "scala.collection.Iterator",
    "scala.collection.Iterable"
  )

  override val alreadyConvertedPrefixes: Set[String] = Set("java.")

  override def getText: String = ScalaBundle.message("implicit.scala.to.java.conversions.hint")

  override def getFamilyName: String = ImplicitConvertScalaToJavaCollectionIntention.getFamilyName

  override val importPath: String = "scala.collection.JavaConversions._"

  override val maybeReplaceAsMethod: Option[String] = None
}

object ImplicitConvertScalaToJavaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("implicit.scala.to.java.conversions.name")
}
