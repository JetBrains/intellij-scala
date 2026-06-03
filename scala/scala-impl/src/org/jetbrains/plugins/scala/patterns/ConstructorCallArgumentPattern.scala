package org.jetbrains.plugins.scala.patterns

import com.intellij.patterns.{PatternCondition, PsiJavaPatterns, PsiMethodPattern}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

class ConstructorCallArgumentPattern(
  className: String,
  index: Int,
  parameterTypes: Array[String]
) extends PatternCondition[ScalaPsiElement]("constructorCallArgument"){

  private val constrPattern1: PsiMethodPattern = constructorPattern(true)
  private val constrPattern2: PsiMethodPattern = constructorPattern(false)

  override def accepts(host: ScalaPsiElement, context: ProcessingContext): Boolean = {
    val constrPattern: PsiMethodPattern = if (host.newCollectionsFramework) constrPattern1 else constrPattern2
    ScalaElementPatternImpl.isConstructorCallArgument(host, context, index, constrPattern, host.isScala3)
  }

  private def constructorPattern(useNewCollectionFramework: Boolean): PsiMethodPattern = {
    val parameterTypesTransformed = parameterTypes.map(transformVarargParameterType(_: String, useNewCollectionFramework))
    PsiJavaPatterns.psiMethod.constructor(true).definedInClass(className).withParameters(parameterTypesTransformed: _*)
  }

  private def transformVarargParameterType(`type`: String, useNewCollectionFramework: Boolean): String = {
    //note, due to type erasure, the original type is ignored
    if (!`type`.endsWith("*")) `type`
    else if (useNewCollectionFramework) CommonQualifiedNames.CollectionImmutableSeq
    else CommonQualifiedNames.CollectionSeq
  }
}