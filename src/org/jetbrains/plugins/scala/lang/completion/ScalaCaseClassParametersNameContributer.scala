package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
  * Created by kate
  * on 1/29/16
  */
class ScalaCaseClassParametersNameContributer extends ScalaCompletionContributor {
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {

    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, _result: CompletionResultSet) {
      val position = positionFromParameters(parameters)

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

    def byTypeCompletionsItems(position: PsiElement, parameter: Option[ScParameter], result: CompletionResultSet): Unit = {
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

  /**
    * Enable completion for object with unapply/unapplySeq methods on case Lable position.
    * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
    * this handler add open and closed brackets to treat element as ScCodeReferenceElement
    * and run ScalaBasicCompletionContributor.
    */
  extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(classOf[ScReferencePattern]).withSuperParent(2, classOf[ScCaseClause]), new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      def typeIdentifierIn(element: Option[ScPattern]): Option[PsiElement] =
        element.flatMap(_.depthFirst().find(_.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER))

      def createCaseClassPatern(text: String, pattern: PsiElement): Option[ScPattern] = {
        Option(ScalaPsiElementFactory.createCaseClauseFromTextWithContext(text + "()", pattern.getContext.getContext, pattern.getContext)).flatMap(_.pattern)
      }

      class MyConsumer(resultSet: CompletionResultSet) extends Consumer[CompletionResult] {
        override def consume(completionResult: CompletionResult): Unit = {
          completionResult.getLookupElement.getPsiElement match {
            case obj: ScObject =>
              obj.members.foreach {
                case fundef: ScFunctionDefinition if fundef.getName == "unapply" || fundef.getName == "unapplySeq" =>
                  resultSet.consume(completionResult.getLookupElement)
                case _ =>
              }
            case _ =>
          }
        }
      }

      def handleCompletionForLowerLetterObject(pattern: PsiElement, result: CompletionResultSet, completionParameters: CompletionParameters): Unit = {
        val currentPrefix = result.getPrefixMatcher.getPrefix
        val element = createCaseClassPatern(currentPrefix, pattern)
        val typeIdentifierInElement = typeIdentifierIn(element)

        typeIdentifierInElement.foreach { psiElement =>
          val identifier = completionParameters.withPosition(psiElement, psiElement.getTextRange.getEndOffset)
          result.runRemainingContributors(identifier, new MyConsumer(result), true)
        }
      }

      val position = positionFromParameters(parameters)
      handleCompletionForLowerLetterObject(position, result, parameters)
    }
  })
}
