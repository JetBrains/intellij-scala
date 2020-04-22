package org.jetbrains.plugins.scala.lang.resolve

import java.util

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.ExtensionPointDeclaration


abstract class SyntheticClassProducer {

  def findClasses(fqn: String, scope: GlobalSearchScope): Array[PsiClass]
}

object SyntheticClassProducer
  extends ExtensionPointDeclaration[SyntheticClassProducer](
    "org.intellij.scala.scalaSyntheticClassProducer"
  ) {

  private var allProducers: Seq[SyntheticClassProducer] = _
  private def getAllProducers = {
    if (allProducers == null)
      allProducers = implementations
    allProducers
  }

  def getAllClasses(fqn: String, scope: GlobalSearchScope): Array[PsiClass] = {
    val all = new util.ArrayList[PsiClass]
    for (ex <- getAllProducers) {
      all.addAll(util.Arrays.asList(ex.findClasses(fqn, scope): _*))
    }
    all.toArray(new Array[PsiClass](0))
  }
}