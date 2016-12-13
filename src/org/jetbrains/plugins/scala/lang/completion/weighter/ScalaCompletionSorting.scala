package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor.ReferenceWithElement
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil, ScalaSmartCompletionContributor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Created by Kate Ustyuzhanina on 12/2/16.
  */
object ScalaCompletionSorting {
  def addScalaSorting(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet = {
    val isSmart = parameters.getCompletionType == CompletionType.SMART
    val position = ScalaCompletionUtil.positionFromParameters(parameters)
    val isAfterNew = ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)

    var sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)

    val expectedTypes: Array[ScType] = ScalaSmartCompletionContributor.extractReference(position) match {
      case Some(ReferenceWithElement(reference, _)) => reference.expectedTypes()
      case _ if isAfterNew =>
        Option(PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition])).map(_.expectedTypes()).getOrElse(Array.empty);
      case _ => Array.empty
    }

    if (!isSmart && isAfterNew) {
      sorter = sorter.weighBefore("liftShorter", new ScalaByTypeWeigher(position))
      sorter = sorter.weighAfter("scalaTypeCompletionWeigher", new ScalaByExpectedTypeWeigher(expectedTypes, position))
    } else if (!isSmart) {
      sorter = sorter.weighBefore("scalaContainingClassWeigher", new ScalaByTypeWeigher(position))
      sorter = sorter.weighAfter("scalaKindWeigher", new ScalaByExpectedTypeWeigher(expectedTypes, position))
    }

    result.withRelevanceSorter(sorter)
  }

}
