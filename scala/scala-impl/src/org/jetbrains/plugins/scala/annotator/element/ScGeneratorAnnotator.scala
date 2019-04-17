package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.annotationHolder.{DelegateAnnotationHolder, ErrorIndication}
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromGeneratorIntentionAction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator}

object ScGeneratorAnnotator extends ElementAnnotator[ScGenerator] {
  override def annotate(element: ScGenerator, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    checkGenerator(element, holder, typeAware)

    element.valKeyword match {
      case Some(valKeyword) =>
        val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("generator.val.keyword.removed"))
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        annotation.registerFix(new RemoveValFromGeneratorIntentionAction(element))
      case _ =>
    }
  }

  private def checkGenerator(generator: ScGenerator, holder: AnnotationHolder, typeAware: Boolean): Unit = {

    for {
      forExpression <- generator.forStatement
      ScEnumerator.withDesugaredAndEnumeratorToken(desugaredGenerator, generatorToken) <- Some(generator)
    } {
      val sessionForDesugaredCode = new AnnotationSession(desugaredGenerator.analogMethodCall.getContainingFile)
      def delegateHolderFor(element: PsiElement) = new DelegateAnnotationHolder(element, holder, sessionForDesugaredCode) with ErrorIndication

      val followingEnumerators = generator.nextSiblings
        .takeWhile(!_.isInstanceOf[ScGenerator])

      val foundUnresolvedSymbol = followingEnumerators
        .exists {
          case enum@ScEnumerator.withDesugaredAndEnumeratorToken(desugaredEnum, enumToken) =>
            val holder = delegateHolderFor(enumToken)
            // TODO decouple
            desugaredEnum.callExpr.foreach(ScReferenceAnnotator.qualifierPart(_, holder, typeAware))
            holder.hadError
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
                val holder = delegateHolderFor(nextGen)
                // TODO decouple
                ScExpressionAnnotator.checkExpressionType(nextDesugaredGen.analogMethodCall, holder, typeAware)
                holder.hadError
            }

            if (!foundMonadicError) {
              val holder = delegateHolderFor(nextGen)
              // TODO decouple
              desugaredGenerator.callExpr.foreach(ScReferenceAnnotator.annotateReference(_, holder))
              foundMonadicError = holder.hadError
            }
          }
        }

        if (!foundMonadicError) {
          // TODO decouple
          desugaredGenerator.callExpr.foreach(ScReferenceAnnotator.qualifierPart(_, delegateHolderFor(generatorToken), typeAware))
        }
      }
    }
  }
}


