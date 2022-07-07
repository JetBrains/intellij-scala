package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.psi.Property
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.{psiElement, psiFile}
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.StandardPatterns.instanceOf
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt}
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.isScalacOption

object SbtPsiElementPatterns {
  def sbtFilePattern: Capture[PsiElement] = psiElement.inFile {
    psiFile.withFileType(instanceOf(SbtFileType.getClass))
  }

  def scalaFilePattern: Capture[PsiElement] = inBuildModule and psiElement.inFile {
    psiFile.withFileType(instanceOf(classOf[ScalaFileType]))
  }

  def propertiesFilePattern: Capture[PsiElement] = psiElement.inFile {
    psiFile.withFileType(instanceOf(classOf[PropertiesFileType]))
  }

  def inBuildModule: Capture[PsiElement] = psiElement(classOf[PsiElement]).`with`(new PatternCondition[PsiElement]("isSbtModuleIdPattern") {
    override def accepts(elem: PsiElement, context: ProcessingContext): Boolean =
      elem.module.exists(_.isBuildModule)
  })

  def sbtModuleIdPattern: Capture[PsiElement] = psiElement(classOf[PsiElement]).`with`(new PatternCondition[PsiElement]("isSbtModuleIdPattern") {
    override def accepts(elem: PsiElement, context: ProcessingContext): Boolean = {
      elem match {
        case expr: ScInfixExpr => expr.left.textMatches("libraryDependencies") && SEQ_ADD_OPS.contains(expr.operation.refName) || SBT_MODULE_ID_TYPE.contains(expr.`type`().getOrAny.canonicalText)
        case patDef: ScPatternDefinition =>
          SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText) || SBT_MODULE_ID_TYPE.exists(patDef.`type`().getOrAny.canonicalText.contains)
        case _ => false
      }

    }
  })

  def scalacOptionsReferencePattern: Capture[ScReferenceExpression] = psiElement(classOf[ScReferenceExpression])
    .`with`(new PatternCondition[ScReferenceExpression]("isScalacOptionsReferencePattern") {
      override def accepts(expr: ScReferenceExpression, context: ProcessingContext): Boolean = isScalacOption(expr)
    })

  def scalacOptionsStringLiteralPattern: Capture[ScStringLiteral] = psiElement(classOf[ScStringLiteral])
    .`with`(new PatternCondition[ScStringLiteral]("isScalacOptionsStringLiteralPattern") {
      override def accepts(expr: ScStringLiteral, context: ProcessingContext): Boolean = isScalacOption(expr)
    })

  def versionPattern: Capture[PsiElement] = psiElement(classOf[PsiElement]).`with`(new PatternCondition[PsiElement]("isVersionPattern") {
    override def accepts(elem: PsiElement, context: ProcessingContext): Boolean = {
      elem match {
        case infix: ScInfixExpr if infix.operation.refName == ":=" =>
          infix.left match {
            /* ThisBuild / scalaVersion := ... */
            case subInfix: ScInfixExpr =>
              subInfix.operation.refName == "/" && subInfix.right.textMatches("scalaVersion")
            /* scalaVersion := ... */
            case other =>
              other.textMatches("scalaVersion")
          }
        case property: Property =>
          property.getKey == "sbt.version" && property.getValue.contains(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
        case _ => false
      }
    }
  })

}
