package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

sealed abstract class UnitMethodInspection extends AbstractMethodSignatureInspection {

  override protected def isApplicable(function: ScFunction): Boolean =
    function.hasUnitResultType
}

object UnitMethodInspection {

  import quickfix._

  final class Parameterless extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) &&
        function.isParameterless &&
        function.superMethods.isEmpty

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] =
      Some(new AddEmptyParentheses(function))
  }

  final class ExplicitType extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.isInstanceOf[ScFunctionDeclaration]

    override protected def findProblemElement(function: ScFunction): Option[PsiElement] =
      function.returnTypeElement

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
      val quickFix = new AbstractFixOnPsiElement(
        ScalaInspectionBundle.message("remove.redundant.type.annotation"),
        function
      ) {
        override protected def doApplyFix(function: ScFunction)(implicit project: Project): Unit =
          removeTypeElement(function)
      }

      Some(quickFix)
    }
  }

  final class FunctionDefinition extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.isInstanceOf[ScFunctionDefinition]

    override protected def findProblemElement(function: ScFunction): Option[PsiElement] =
      function.returnTypeElement

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
      val quickFix = new AbstractFixOnPsiElement(
        ScalaInspectionBundle.message("remove.redundant.type.annotation.and.equals.sign"),
        function.asInstanceOf[ScFunctionDefinition]
      ) {
        override protected def doApplyFix(function: ScFunctionDefinition)(implicit project: Project): Unit = {
          removeTypeElement(function)
          removeAssignment(function)
        }
      }

      Some(quickFix)
    }
  }

  final class ExplicitAssignment extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.isInstanceOf[ScFunctionDefinition] &&
        !function.hasExplicitType &&
        !function.isConstructor

    override protected def findProblemElement(function: ScFunction): Option[PsiElement] =
      function.asInstanceOf[ScFunctionDefinition].assignment

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
      val quickFix = new AbstractFixOnPsiElement(
        ScalaInspectionBundle.message("remove.redundant.equals.sign"),
        function.asInstanceOf[ScFunctionDefinition]
      ) {
        override protected def doApplyFix(function: ScFunctionDefinition)(implicit project: Project): Unit =
          removeAssignment(function)
      }

      Some(quickFix)
    }
  }

  final class ProcedureDefinition extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.isInstanceOf[ScFunctionDefinition] &&
        !function.hasAssign &&
        !function.isConstructor &&
        IntentionAvailabilityChecker.checkInspection(this, function)

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
      val quickFix = new AbstractFixOnPsiElement(
        ScalaInspectionBundle.message("convert.to.function.syntax"),
        function.asInstanceOf[ScFunctionDefinition]
      ) {
        override protected def doApplyFix(function: ScFunctionDefinition)(implicit project: Project): Unit = {
          removeAssignment(function)
          removeTypeElement(function)
          addUnitTypeElement(function)
        }
      }

      Some(quickFix)
    }
  }

  final class ProcedureDeclaration extends UnitMethodInspection {

    override protected def isApplicable(function: ScFunction): Boolean =
      super.isApplicable(function) && function.isInstanceOf[ScFunctionDeclaration] &&
        !function.hasExplicitType

    override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
      val quickFix = new AbstractFixOnPsiElement(
        ScalaInspectionBundle.message("convert.to.function.syntax"),
        function
      ) {
        override protected def doApplyFix(function: ScFunction)(implicit project: Project): Unit = {
          def addChildNode(element: PsiElement): Unit =
            function.getNode.addChild(element.getNode)

          import ScalaPsiElementFactory.{createColon, createTypeElementFromText, createWhitespace}
          addChildNode(createColon)
          addChildNode(createWhitespace)
          addChildNode(createTypeElementFromText("Unit"))
        }
      }

      Some(quickFix)
    }
  }

}