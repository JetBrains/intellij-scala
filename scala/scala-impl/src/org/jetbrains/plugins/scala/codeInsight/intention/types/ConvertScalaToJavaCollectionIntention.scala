package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Converts expression representing scala collection to
 * java equivalent using [[scala.collection.JavaConverters]] before Scala 2.13
 * and [[scala.jdk.CollectionConverters]] since Scala 2.13
 */
class ConvertScalaToJavaCollectionIntention extends BaseJavaConvertersIntention("asJava") {

  override val targetCollections = Set(
    "scala.collection.Seq",
    "scala.collection.Set",
    "scala.collection.Map",
    "scala.collection.Iterator",
    "scala.collection.Iterable"
  )

  override val alreadyConvertedPrefixes: Set[String] = Set("java.")

  override def getText: String = ScalaBundle.message("convert.scala.to.java.collection.hint")

  override def getFamilyName: String = ConvertScalaToJavaCollectionIntention.getFamilyName

}

object ConvertScalaToJavaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("convert.scala.to.java.collection.name")
}
