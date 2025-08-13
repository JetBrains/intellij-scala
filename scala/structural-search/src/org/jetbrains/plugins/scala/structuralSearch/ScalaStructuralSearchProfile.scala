package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.{StructuralSearchProfile, StructuralSearchProfileBase}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaFileTemplateContextType
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

final class ScalaStructuralSearchProfile extends StructuralSearchProfileBase {
  override protected def getVarPrefixes: Array[String] = Array[String]("__$_")

  override def isMyLanguage(@NotNull language: Language): Boolean =
    language == ScalaLanguage.INSTANCE || language == Scala3Language.INSTANCE

  override def getContext(pattern: String, @Nullable language: Language, contextId: String): String =
    StructuralSearchProfile.PATTERN_PLACEHOLDER

  override def getTemplateContextTypeClass: Class[_ <: TemplateContextType] = classOf[ScalaFileTemplateContextType]

  override def createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor =
    new ScalaMatchingVisitor(globalVisitor)

  override def compile(elements: Array[PsiElement], globalVisitor: GlobalCompilingVisitor): Unit = {
    new ScalaCompilingVisitor(globalVisitor).compile(elements)
  }

  // use this to configure which modifier should be shown
  override def isApplicableConstraint(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    super.isApplicableConstraint(constraintName, variableNode, completePattern, target)

  // if a variable is set inside the template, normally getText is used to extract the name
  // we can also use getTypedVarString to extract the name (e.g. special for an annotation
}
