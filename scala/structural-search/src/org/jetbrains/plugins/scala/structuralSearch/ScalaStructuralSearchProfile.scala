package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.Language
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.{CompiledPattern, GlobalMatchingVisitor}
import com.intellij.structuralsearch.plugin.ui.UIUtil
import com.intellij.structuralsearch.{StructuralSearchProfile, StructuralSearchProfileBase}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.codeInsight.template.impl.{ScalaCodeContextType, ScalaFileTemplateContextType}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

final class ScalaStructuralSearchProfile extends StructuralSearchProfileBase {
  override protected def getVarPrefixes: Array[String] = Array("__$_")

  override def isMyLanguage(@NotNull language: Language): Boolean =
    language == ScalaLanguage.INSTANCE || language == Scala3Language.INSTANCE

  override def getContext(pattern: String, @Nullable language: Language, contextId: String): String =
    StructuralSearchProfile.PATTERN_PLACEHOLDER

  override def getTemplateContextTypeClass: Class[_ <: TemplateContextType] = classOf[ScalaFileTemplateContextType]

  override def createMatchingVisitor(globalVisitor: GlobalMatchingVisitor): PsiElementVisitor =
    ScalaMatchingVisitor(globalVisitor)

  override def compile(elements: Array[PsiElement], globalVisitor: GlobalCompilingVisitor): Unit = {
    new ScalaCompilingVisitor(globalVisitor).compile(elements)
  }

  // use this to configure which modifier should be shown
  override def isApplicableConstraint(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    constraintName match {
      case UIUtil.MINIMUM_ZERO =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
      case UIUtil.MAXIMUM_UNLIMITED =>
        isMinMaxApplicable(constraintName, variableNode, completePattern, target)
      case _ =>
        super.isApplicableConstraint(constraintName, variableNode, completePattern, target)
    }

  private def isMinMaxApplicable(constraintName: String, variableNode: PsiElement, completePattern: Boolean, target: Boolean): Boolean =
    if (completePattern || target || variableNode == null) return false
    variableNode.getParent match {
      case parent => parent.getParent match {
        case _: ScImportExpr => false
        case grandParent: ScSimpleTypeElement =>
          grandParent.getParent match {
            case _: ScParameterType => false
            case _ => true
          }
        case _ => true
      }
    }

  // if a variable is set inside the template, normally getText is used to extract the name
  // we can also use getTypedVarString to extract the name (e.g. special for an annotation

  override def createCompiledPattern(): CompiledPattern =
    new CompiledPattern {
      override def getTypedVarPrefixes: Array[String] = getVarPrefixes

      override def getTypedVarString(element: PsiElement): String =
          element match {
            case par: ScNamedElement =>
              par.name
            case annot: ScAnnotation =>
              annot.constructorInvocation.typeElement.getText
            case caseClause: ScCaseClause =>
              caseClause.pattern.map(pat => if pat.bindings.size == 1 then pat.bindings.head.getText else pat.getText).getOrElse("")
            case valvar: ScValueOrVariable =>
              if (valvar.declaredNames.size == 1) valvar.declaredNames.head
              else super.getTypedVarString(valvar)
            case constrInv: ScConstructorInvocation =>
              constrInv.typeElement.getText
            case typeElement: ScTypeElement =>
              typeElement.getFirstChild.getText
            case _ =>
              super.getTypedVarString(element)
          }

      override def isTypedVar(str: String): Boolean = {
        !str.contains(' ') && getVarPrefixes.exists(str.startsWith)
      }
    }
}
