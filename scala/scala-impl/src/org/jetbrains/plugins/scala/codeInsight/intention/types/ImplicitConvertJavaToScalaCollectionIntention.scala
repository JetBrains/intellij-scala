package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.ScalaBundle

/**
  * Motiejus.Juodelis, 18.11.2019
  */
class ImplicitConvertJavaToScalaCollectionIntention extends BaseJavaConvertersIntention {

  override val targetCollections = Set(
    "java.lang.Iterable",
    "java.util.Iterator",
    "java.util.Collection",
    "java.util.Dictionary",
    "java.util.Map"
  )

  override val alreadyConvertedPrefixes: Set[String] = Set("scala.collections")

  override def getText: String = ScalaBundle.message("implicit.java.to.scala.conversions.hint")

  override def getFamilyName: String = ImplicitConvertScalaToJavaCollectionIntention.getFamilyName

  override val importPath: String = "scala.collection.JavaConversions._"

  override val maybeReplaceAsMethod: Option[String] = None
}

object ImplicitConvertJavaToScalaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("implicit.java.to.scala.conversions.name")
}
