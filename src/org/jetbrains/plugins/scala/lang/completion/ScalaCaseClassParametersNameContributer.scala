package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElementWeigher, LookupElement}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScPatternArgumentList, ScConstructorPattern, ScCaseClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
  * Created by kate
  * on 1/29/16
  */
class ScalaCaseClassParametersNameContributer extends ScalaCompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, _result: CompletionResultSet) {
      val position = positionFromParameters(parameters)
      val scope = PsiTreeUtil.getContextOfType(position, classOf[ScCaseClause])
      if (scope == null) return

      val constructorPattern = PsiTreeUtil.getContextOfType(position, classOf[ScConstructorPattern])
      if (constructorPattern == null) return

      val classRef = constructorPattern.asInstanceOf[ScConstructorPattern].ref
      val caseClassParams = classRef.resolve() match {
        case funcDef: ScFunctionDefinition if funcDef.syntheticCaseClass.isDefined =>
          funcDef.syntheticCaseClass.get.parameters
        case fundef: ScFunctionDefinition
          if fundef.getName == "unapply" || fundef.getName == "unapplySeq" =>
          fundef.getParameterList.params
        case _ => return
      }

      if (caseClassParams.isEmpty) return

      val parameterWithPosition = getCorrespondedParameterForPosition(position, caseClassParams)

      val corespondedParameter = parameterWithPosition.parameter
      val myPosition = parameterWithPosition.position

      val result = addByOrderSorter(parameters, _result, myPosition, caseClassParams)

      byClassParamCompletionsItems(caseClassParams, result)
      byTypeCompletionsItems(position, corespondedParameter, result)
    }

    def byTypeCompletionsItems(position: PsiElement, parameter: Option[ScParameter], result: CompletionResultSet) = {
      position.getContext match {
        case pattern: ScPattern if pattern.expectedType.isDefined && parameter.isDefined =>
          val lookups =
            NameSuggester.suggestNamesByType(pattern.expectedType.get).map(name => new ScalaLookupItem(parameter.get, name))
          lookups.foreach(l => addLocalScalaLookUpItem(result, l))
        case _ =>
      }
    }

    def byClassParamCompletionsItems(params: Seq[ScParameter], result: CompletionResultSet): Unit = {
      params.map(p => new ScalaLookupItem(p, p.name)).foreach(l => addLocalScalaLookUpItem(result, l))
    }

    def addByOrderSorter(parameters: CompletionParameters, result: CompletionResultSet,
                         currentPosition: Int, classParams: Seq[ScParameter]): CompletionResultSet = {

      class PreferByParamsOrder extends LookupElementWeigher("orderByPosition") {
        override def weigh(item: LookupElement): Comparable[_] = {
          ScalaLookupItem.original(item) match {
            case s: ScalaLookupItem =>
              s.element match {
                case param: ScParameter if param.name == s.name /*not equals when name computed by type*/ =>
                  val positionInClassParameters = classParams.indexOf(param)
                  if (currentPosition == positionInClassParameters) -1
                  else math.abs(currentPosition - positionInClassParameters)
                case _ => 0
              }
            case _ => null
          }
        }
      }

      var sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)
      sorter = sorter.weighAfter("prefix", new PreferByParamsOrder())
      result.withRelevanceSorter(sorter)
    }

    private def addLocalScalaLookUpItem(result: CompletionResultSet, lookupElement: ScalaLookupItem): Unit = {
      lookupElement.isLocalVariable = true
      result.addElement(lookupElement)
    }

    private def getCorrespondedParameterForPosition(position: PsiElement, classParams: Seq[ScParameter]): ParameterWithPosition = {
      val me = PsiTreeUtil.getContextOfType(position, classOf[ScPattern])
      if (me == null) return ParameterWithPosition(None, -1)

      val patterns = Option(PsiTreeUtil.getContextOfType(position, classOf[ScPatternArgumentList])).map(_.patterns)

      if (patterns.isEmpty || (patterns.isDefined && patterns.get.length > classParams.length))
        return ParameterWithPosition(None, -1) //try to type more param than can be

      val myPosition = patterns.get.indexOf(me)
      val coresponedParameter =
        if ((myPosition >= 0) && (myPosition != classParams.length)) Some(classParams.apply(myPosition)) else None

      ParameterWithPosition(coresponedParameter, myPosition)
    }

    case class ParameterWithPosition(parameter: Option[ScParameter], position: Int)

  })
}
