package org.jetbrains.plugins.scala
package annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiNamedElement}
import org.jetbrains.plugins.scala.annotator.template.kindOf
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
 * @author Aleksander Podkhalyuzin
 * Date: 25.03.2009
 */

// TODO move to org.jetbrains.plugins.scala.lang.psi.annotator
object AnnotatorUtils {

  def checkConformance(expression: ScExpression, typeElement: ScTypeElement)
                      (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = expression

    if (ScMethodType.hasMethodType(expression)) {
      return
    }

    expression.getTypeAfterImplicitConversion().tr.foreach {actual =>
      val expected = typeElement.calcType
      if (!actual.conforms(expected) && !shouldIgnoreTypeMismatchIn(expression)) {
        TypeMismatchError.register(expression, expected, actual, blockLevel = 1) { (expected, actual) =>
          ScalaBundle.message("type.mismatch.found.required", actual, expected)
        }
      }
    }
  }

  def shouldIgnoreTypeMismatchIn(e: PsiElement, fromFunctionLiteral: Boolean = false): Boolean = {
    // Don't show type a mismatch error when there's a parser error, SCL-16899, SCL-17206
    def hasParserErrors = e.elements.exists(_.isInstanceOf[PsiErrorElement]) ||
      e.getPrevSibling.isInstanceOf[PsiErrorElement] ||
      e.getNextSibling.isInstanceOf[PsiErrorElement] ||
      e.getNextSibling.isInstanceOf[LeafPsiElement] && e.getNextSibling.textMatches(".") && e.getNextSibling.getNextSibling.isInstanceOf[PsiErrorElement] ||
      e.parent.exists { parent =>
        e == parent.getFirstChild && parent.getPrevSibling.isInstanceOf[PsiErrorElement] ||
          e == parent.getLastChild && parent.getNextSibling.isInstanceOf[PsiErrorElement]
      }

    // Don't show type mismatch for a whole function literal when result type doesn't match, SCL-16901

    def isFunctionLiteral = e match {
      case _: ScFunctionExpr | ScBlock(_: ScFunctionExpr) => true
      case _ => false
    }

    def isResultOfFunctionLiteral = e match {
      case Parent(_: ScFunctionExpr) => true
      case Parent(Parent((_: ScFunctionExpr) && Parent(_: ScBlockExpr))) => true
      case _ => false
    }

    def hasUnresolvedReferences = e.elements.exists(_.asOptionOf[ScReference].exists(_.multiResolveScala(false).isEmpty))

    hasParserErrors ||
      hasUnresolvedReferences ||
      !fromFunctionLiteral && (isFunctionLiteral || isResultOfFunctionLiteral)
  }

  //fix for SCL-7176
  def checkAbstractMemberPrivateModifier(element: PsiElement, toHighlight: Seq[PsiElement])
                                        (implicit holder: ScalaAnnotationHolder): Unit = {
    element match {
      case fun: ScFunctionDeclaration if fun.isNative =>
      case modOwner: ScModifierListOwner =>
        modOwner.getModifierList.accessModifier match {
          case Some(am) if am.isUnqualifiedPrivateOrThis =>
            for (e <- toHighlight) {
              val annotation = holder.createErrorAnnotation(e, ScalaBundle.message("abstract.member.not.have.private.modifier"))
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            }
          case _ =>
        }
      case _ =>
    }
  }

  def registerTypeMismatchError(actualType: ScType, expectedType: ScType,
                                expression: ScExpression)
                               (implicit holder: ScalaAnnotationHolder): Unit = {
    // See comments in ScMethodType.hasMethodType
    // The workaround is nice but there is a situation where we want to show the mismatch error with function types:
    // => namely if a function type is expected
    if (!FunctionType.isFunctionType(expectedType) && ScMethodType.hasMethodType(expression)) {
      return
    }

    //TODO show parameter name
    if (!actualType.conforms(expectedType) && !shouldIgnoreTypeMismatchIn(expression)) {
      TypeMismatchError.register(expression, expectedType, actualType) { (expected, actual) =>
        ScalaBundle.message("type.mismatch.expected.actual", expected, actual)
      }
    }
  }

  def annotationWithoutHighlighting(te: PsiElement)
                                   (implicit holder: ScalaAnnotationHolder): ScalaAnnotation = {
    val teAnnotation = holder.createErrorAnnotation(te, null)
    teAnnotation.setHighlightType(ProblemHighlightType.INFORMATION)
    val emptyAttr = new TextAttributes()
    teAnnotation.setEnforcedTextAttributes(emptyAttr)
    teAnnotation
  }

  /**
    * This method will return checked conformance if it's possible to check it.
    * In other way it will return true to avoid red code.
    * Check conformance in case l = r.
    */
  def smartCheckConformance(l: TypeResult, r: TypeResult): Boolean = {
    val leftType = l match {
      case Right(res) => res
      case _ => return true
    }
    val rightType = r match {
      case Right(res) => res
      case _ => return true
    }
    rightType.conforms(leftType)
  }

  // TODO encapsulate
  def highlightImplicitView(expr: ScExpression, fun: PsiNamedElement, typeTo: ScType,
                            elementToHighlight: PsiElement)
                           (implicit holder: ScalaAnnotationHolder): Unit = {
    if (ScalaProjectSettings.getInstance(elementToHighlight.getProject).isShowImplisitConversions) {
      val range = elementToHighlight.getTextRange
      val annotation = holder.createInfoAnnotation(range, null)
      annotation.setTextAttributes(DefaultHighlighter.IMPLICIT_CONVERSIONS)
      annotation.setAfterEndOfLine(false)
    }
  }

  // TODO something more reliable
  object ErrorAnnotationMessage {
    def unapply(definition: ScTypeDefinition): Option[NlsString] =
      Option.when(definition.isSealed)(
        ScalaBundle.nls(
          "illegal.inheritance.from.sealed.kind",
          kindOf(definition, toLowerCase = true),
          definition.name
        )
      )
  }
}