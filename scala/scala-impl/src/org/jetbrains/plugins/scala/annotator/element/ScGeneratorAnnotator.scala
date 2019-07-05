package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{AnnotationHolder, AnnotationSession}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.annotationHolder.{DelegateAnnotationHolder, ErrorIndication}
import org.jetbrains.plugins.scala.codeInspection.caseClassParamInspection.RemoveValFromGeneratorIntentionAction
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerator, ScGenerator}

object ScGeneratorAnnotator extends ElementAnnotator[ScGenerator] {

  override def annotate(element: ScGenerator, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    checkGenerator(element, typeAware)

    element.valKeyword match {
      case Some(valKeyword) =>
        val annotation = holder.createWarningAnnotation(valKeyword, ScalaBundle.message("generator.val.keyword.removed"))
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        annotation.registerFix(new RemoveValFromGeneratorIntentionAction(element))
      case _ =>
    }
  }

  private def checkGenerator(generator: ScGenerator, typeAware: Boolean)
                            (implicit holder: AnnotationHolder): Unit = {

    for {
      forExpression <- generator.forStatement
      ScEnumerator.withDesugaredAndEnumeratorToken(desugaredGenerator, generatorToken) <- Some(generator)
      session = new AnnotationSession(desugaredGenerator.analogMethodCall.getContainingFile)
    } {
      val followingEnumerators = generator.nextSiblings
        .takeWhile(!_.isInstanceOf[ScGenerator])

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
              desugaredGenerator.callExpr.foreach(ScReferenceAnnotator.annotateReference(_, inDesugaring = true)(errorHolder))
              foundMonadicError = errorHolder.hadError
            }
          }
        }

        if (!foundMonadicError) {
          // TODO decouple
          desugaredGenerator.callExpr.foreach(ScReferenceAnnotator.qualifierPart(_, typeAware)(delegateHolderFor(generatorToken, session)))
        }
      }
    }
  }

  private def delegateHolderFor(target: PsiElement, session: AnnotationSession)
                               (implicit holder: AnnotationHolder) =
    new DelegateAnnotationHolder(session) with ErrorIndication {

      override protected val element: Some[PsiElement] = Some(target)

      override protected def transformRange(range: TextRange): TextRange = target.getTextRange
    }
}


