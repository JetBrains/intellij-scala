package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Converts expression representing java collection to
 * scala equivalent using [[scala.collection.JavaConverters]] before Scala 2.13
 * and [[scala.jdk.CollectionConverters]] since Scala 2.13
 */
class ConvertJavaToScalaCollectionIntention extends BaseJavaConvertersIntention("asScala") {

  override val targetCollections = Set(
    "java.lang.Iterable",
    "java.util.Iterator",
    "java.util.Collection",
    "java.util.Dictionary",
    "java.util.Map"
  )

  override val alreadyConvertedPrefixes: Set[String] = Set("scala.collection")

  override def getText: String = ScalaBundle.message("convert.java.to.scala.collection.hint")

  override def getFamilyName: String = ConvertJavaToScalaCollectionIntention.getFamilyName
}

object ConvertJavaToScalaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("convert.java.to.scala.collection.name")
}