package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@Internal
abstract class SyntheticClassProducer {

  def findClasses(fqn: String, scope: GlobalSearchScope): Array[PsiClass]
}

object SyntheticClassProducer
  extends ExtensionPointDeclaration[SyntheticClassProducer](
    "org.intellij.scala.scalaSyntheticClassProducer"
  ) {


  def getAllClasses(fqn: String, scope: GlobalSearchScope): Array[PsiClass] =
    implementations.toArray.flatMap(_.findClasses(fqn, scope))
}