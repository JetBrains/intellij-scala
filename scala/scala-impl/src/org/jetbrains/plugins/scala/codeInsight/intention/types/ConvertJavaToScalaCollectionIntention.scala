package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Converts expression representing java collection to
 * scala equivalent using [[scala.collection.JavaConverters]] before Scala 2.13
 * and [[scala.jdk.CollectionConverters]] since Scala 2.13
 */
class ConvertJavaToScalaCollectionIntention extends BaseJavaConvertersIntention("asScala") {

  override def targetCollections(project: Project, scope: GlobalSearchScope): Set[PsiClass] = {
    val facade = JavaPsiFacade.getInstance(project)
    Set(
      "java.lang.Iterable",
      "java.util.Iterator",
      "java.util.Collection",
      "java.util.Dictionary",
      "java.util.Map"
    ).flatMap(fqn => Option(facade.findClass(fqn, scope)))
  }

  @SafeFieldForPreview
  override val alreadyConvertedPrefixes: Set[String] = Set("scala.collection")

  override def getText: String = ScalaBundle.message("convert.java.to.scala.collection.hint")

  override def getFamilyName: String = ConvertJavaToScalaCollectionIntention.getFamilyName
}

object ConvertJavaToScalaCollectionIntention {
  def getFamilyName: String = ScalaBundle.message("convert.java.to.scala.collection.name")
}
