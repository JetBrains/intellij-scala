package org.jetbrains.sbt.language.completion

import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.{psiElement, psiFile}
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.StandardPatterns.instanceOf
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.sbt.language.SbtFileType

object SbtPsiElementPatterns {
  def sbtFilePattern: Capture[PsiElement] = psiElement.inFile {
    psiFile.withFileType(instanceOf(SbtFileType.getClass))
  }

  def scalaFilePattern: Capture[PsiElement] = psiElement.inFile {
    psiFile.withFileType(instanceOf(classOf[ScalaFileType]))
  }

  def sbtModuleIdPattern: Capture[PsiElement] = psiElement(classOf[PsiElement]).`with`(new PatternCondition[PsiElement]("isSbtModuleIdPattern") {
    override def accepts(elem: PsiElement, context: ProcessingContext): Boolean = {
      elem match {
        case expr: ScInfixExpr => SBT_MODULE_ID_TYPE.contains(expr.`type`().getOrAny.canonicalText)
        case patDef: ScPatternDefinition => SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText)
        case _ => false
      }

    }
  })

}
