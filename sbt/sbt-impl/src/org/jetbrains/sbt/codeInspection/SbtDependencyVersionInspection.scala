package org.jetbrains.sbt.codeInspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions.&
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.DependencyDescriptor
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.sbt.SbtBundle

class SbtDependencyVersionInspection extends DependencyVersionInspection {
  override protected def isAvailable(element: PsiElement): Boolean =
    SbtCodeInspectionUtils.isAvailable(element)

  override protected def createDependencyDescriptor(element: PsiElement): Option[DependencyDescriptor] =
    SbtCodeInspectionUtils.createDependencyDescriptor(element)

  override protected def createQuickFix(element: PsiElement, newerVersion: String): LocalQuickFix =
    new SbtUpdateDependencyVersionQuickFix(element, newerVersion)
}

class SbtUpdateDependencyVersionQuickFix(elem: PsiElement, newVer: String)
  extends AbstractFixOnPsiElement(SbtBundle.message("packagesearch.update.dependency.to.newer.stable.version", newVer), elem) {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    val features = ScalaFeatures.default
    element match {
      case str: ScStringLiteral =>
        str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\"", features))
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case (_: ScReferencePattern) & inNameContext(ScPatternDefinition.expr(expr)) => expr match {
            case str: ScStringLiteral =>
              str.replace(createExpressionFromText("\"" + StringUtil.escapeStringCharacters(newVer) + "\"", features))
            case _ =>
          }
        }
      case _ => // do nothing
    }
  }
}
