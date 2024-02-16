package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.annotationHolder.{DelegateAnnotationHolder, ErrorIndication}
import org.jetbrains.plugins.scala.annotator.element.ScForBindingAnnotator.RemoveCaseFromPatternedEnumeratorFix
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationBuilder, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromGeneratorIntentionAction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScFor, ScGenerator, ScMethodCall, ScReferenceExpression}

import scala.annotation.nowarn
import scala.math.Ordering.Implicits._

object ScForAnnotator extends ElementAnnotator[ScFor] {

  override def annotate(scFor: ScFor, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    val generators = scFor.enumerators.toSeq.flatMap(_.generators)
    generators.foreach { generator =>
      checkGenerator(generator, typeAware)

      generator.valKeyword.foreach { valKeyword =>
        holder.createWarningAnnotation(
          valKeyword,
          ScalaBundle.message("enumerators.generator.val.keyword.found"),
          ProblemHighlightType.GENERIC_ERROR,
          new RemoveValFromGeneratorIntentionAction(generator)
        )
      }

      if (!generator.features.`case in pattern bindings`) {
        generator.caseKeyword.foreach { caseKeyword =>
          holder.createWarningAnnotation(
            caseKeyword,
            ScalaBundle.message("for.pattern.bindings.require.scala3"),
            ProblemHighlightType.GENERIC_ERROR,
            new RemoveCaseFromPatternedEnumeratorFix(generator)
          )
        }
      }
    }
  }

  private def checkGenerator(generator: ScGenerator, typeAware: Boolean)
                            (implicit holder: ScalaAnnotationHolder): Unit = {

    for {
      forExpression <- generator.forStatement
      ScEnumerator.withDesugaredAndEnumeratorToken(desugaredGenerator, generatorToken) <- Some(generator)
      session = new AnnotationSession(desugaredGenerator.analogMethodCall.getContainingFile): @nowarn("cat=deprecation")
    } {
      val followingEnumerators = generator.nextSiblings
        .takeWhile(!_.is[ScGenerator])

      val foundUnresolvedSymbol = followingEnumerators
        .exists {
          case ScEnumerator.withDesugaredAndEnumeratorToken(desugaredEnum, enumToken) =>
            val errorHolder = delegateHolderFor(enumToken, session)
            // TODO decouple
            desugaredEnum.callExpr.foreach(ScReferenceAnnotator.qualifierPart(_, typeAware)(errorHolder))
            errorHolder.hadError
          case _ =>
            false
        }

      // It doesn't make sense to look for errors down the function chain
      // if we were not able to resolve a previous call
      if (!foundUnresolvedSymbol) {
        var foundMonadicError = false

        // check the return type of the next generator
        // we check the next generator here with it's predecessor,
        // because we don't want to do the check if foundUnresolvedSymbol is true
        if (forExpression.isYield) {
          val nextGenOpt = generator.nextSiblings.collectFirst { case gen: ScGenerator => gen }
          for (nextGen <- nextGenOpt) {
            foundMonadicError = nextGen.desugared.exists {
              nextDesugaredGen =>
                val errorHolder = delegateHolderFor(nextGen, session)
                // TODO decouple
                ScExpressionAnnotator.checkExpressionType(nextDesugaredGen.analogMethodCall, typeAware)(errorHolder)
                errorHolder.hadError
            }

            if (!foundMonadicError) {
              val errorHolder = delegateHolderFor(nextGen, session)
              // TODO decouple
              desugaredGenerator.callExpr.foreach(ScReferenceAnnotator.annotateReference(_)(errorHolder))
              ScMethodInvocationAnnotator.annotateMethodInvocation(desugaredGenerator.analogMethodCall, inDesugaring = true)(errorHolder)
              foundMonadicError = errorHolder.hadError
            }
          }
        }

        if (!foundMonadicError) {
          // TODO decouple
          desugaredGenerator.callExpr.foreach { e =>
            ScReferenceAnnotator.qualifierPart(e, typeAware)(delegateHolderFor(generatorToken, session))

            e.qualifier.flatMap(_.asOptionOf[ScMethodCall].map(_.getInvokedExpr).collect { case re: ScReferenceExpression => re }).foreach {
              ScReferenceAnnotator.qualifierPart(_, typeAware)(delegateHolderFor(generatorToken, session))
            }
          }
        }
      }
    }
  }

  private def delegateHolderFor(target: PsiElement, session: AnnotationSession)
                               (implicit holder: ScalaAnnotationHolder): DelegateAnnotationHolder with ErrorIndication =
    new DelegateAnnotationHolder(session) with ErrorIndication {
      private var _hadError = false
      override def hadError: Boolean = _hadError

      override protected val element: Some[PsiElement] = Some(target)

      override protected def transformRange(range: TextRange): TextRange = target.getTextRange

      override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder = {
        if (severity >= HighlightSeverity.ERROR) {
          _hadError = true
        }
        super.newAnnotation(severity, message)
      }

      override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder = {
        if (severity >= HighlightSeverity.ERROR) {
          _hadError = true
        }
        super.newSilentAnnotation(severity)
      }
    }
}
