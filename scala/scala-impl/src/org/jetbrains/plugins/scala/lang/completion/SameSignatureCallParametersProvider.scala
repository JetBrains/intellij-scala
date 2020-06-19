package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.ui.LayeredIcon
import com.intellij.util.ProcessingContext
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaInsertHandler.AssignmentText
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alefas
 * @since 03.09.13
 */
final class SameSignatureCallParametersProvider extends ScalaCompletionContributor {

  import SameSignatureCallParametersProvider._

  extendBasicAndSmart(classOf[ScMethodCall])(new MethodParametersCompletionProvider)

  extendBasicAndSmart(classOf[ScConstructorInvocation])(new ConstructorParametersCompletionProvider)

  private def extendBasicAndSmart(invocationClass: Class[_ <: ScalaPsiElement])
                                 (provider: CompletionProvider[CompletionParameters]): Unit = {
    val place = identifierWithParentsPattern(
      classOf[ScReferenceExpression],
      classOf[ScArgumentExprList],
      invocationClass
    ).afterLeaf("(")
      .beforeLeaf(")")

    extend(CompletionType.BASIC, place, provider)
    extend(CompletionType.SMART, place, provider)
  }

}

object SameSignatureCallParametersProvider {

  private final class MethodParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] = {
      val argumentsList = findArgumentsList(position)

      argumentsList.getContext match {
        case ScMethodCall.withDeepestInvoked(reference: ScReferenceExpression) =>
          val clauseIndex = argumentsList.invocationCount - 1

          createFunctionArgumentsElements(reference, clauseIndex)() ++
            createAssignmentElements(reference, clauseIndex)
        case _ => Iterable.empty
      }
    }

    private def createFunctionArgumentsElements(reference: ScReferenceExpression,
                                                clauseIndex: Int)
                                               (hasSuperQualifier: Boolean = reference.qualifier.exists(_.isInstanceOf[ScSuperReference])) = for {
      ScalaResolveResult(method: ScMethodLike, substitutor) <- reference.getSimpleVariants(completion = true)
      if method.name == reference.refName

      lookupElement <- createLookupElement(method, clauseIndex, substitutor) {
        findResolvableParameters(reference)
      }
    } yield lookupElement
      .withMoveCaretInsertionHandler
      .withSuperMethodParameters(hasSuperQualifier)

    private def createAssignmentElements(reference: ScReferenceExpression,
                                         clauseIndex: Int) = for {
      ScalaResolveResult(function: ScFunction, substitutor) <- reference.multiResolveScala(incomplete = true).toSeq
      if function.isApplyMethod

      lookupElement <- createLookupElementBySignature(
        function,
        clauseIndex
      )(
        function,
        substitutor
      )
    } yield lookupElement
      .withTailText(AssignmentText)
      .withInsertHandler(new AssignmentsInsertHandler)

    private def findResolvableParameters(reference: PsiElement)
                                        (parameters: Seq[ScParameter]) = {
      val names = parameters.map(_.name)

      val elements = names.map {
        createReferenceFromText(_, reference.getContext, reference)
          .resolve
      }

      if (elements.forall(_.isInstanceOf[Argument]))
        names
          .zip(elements)
          .asInstanceOf[Seq[(String, Argument)]]
      else
        Seq.empty
    }
  }

  private final class ConstructorParametersCompletionProvider extends ScalaCompletionProvider {

    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElementBuilder] =
      getContextOfType(position, classOf[ScTemplateDefinition]) match {
        case ClassConstructor(constructor) =>
          val argumentsList = findArgumentsList(position)
          val constructorInvocation = argumentsList.getContext.asInstanceOf[ScConstructorInvocation]

          constructorInvocation.typeElement match {
            case typeElement@Typeable(tp) =>
              tp.extractClassType match {
                //noinspection ScalaUnnecessaryParentheses
                case Some((clazz: ScClass, substitutor)) if (if (clazz.hasTypeParameters) typeElement.isInstanceOf[ScParameterizedTypeElement] else true) =>
                  val clauseIndex = constructorInvocation.arguments.indexOf(argumentsList)

                  for {
                    extractedClassConstructor <- clazz.constructors

                    lookupElement <- createLookupElementBySignature(
                      extractedClassConstructor,
                      clauseIndex
                    )(
                      constructor,
                      substitutor
                    )
                  } yield lookupElement
                    .withMoveCaretInsertionHandler
                    .withSuperMethodParameters(true)
                case _ => Iterable.empty
              }
            case _ => Iterable.empty
          }
        case _ => Iterable.empty
      }
  }

  private[this] type Argument = PsiElement with Typeable

  private[this] def createLookupElementBySignature(parameterMethod: ScMethodLike,
                                                   clauseIndex: Int)
                                                  (argumentMethod: ScMethodLike,
                                                   substitutor: ScSubstitutor): Option[LookupElementBuilder] =
    createLookupElement(parameterMethod, clauseIndex, substitutor) { _ =>
      argumentMethod
        .parameterList
        .params
        .map(parameter => parameter.name -> parameter)
    }

  private[this] def createLookupElement(method: ScMethodLike,
                                        clauseIndex: Int,
                                        substitutor: ScSubstitutor)
                                       (argumensWithNames: Seq[ScParameter] => Seq[(String, Argument)]) = {
    val parameters = method.parametersInClause(clauseIndex)
    parameters.length match {
      case 0 | 1 => None
      case clauseLength =>
        val nameToArgument = argumensWithNames(parameters).toMap

        applicableNames(parameters, substitutor, nameToArgument) match {
          case names if names.length == clauseLength =>
            //noinspection ZeroIndexToHead
            val first = nameToArgument(names(0))
            val second = nameToArgument(names(1))

            Some {
              LookupElementBuilder
                .create(names.commaSeparated())
                .withCompositeIcon(first, second)
            }
          case _ => None
        }
    }
  }

  private[this] def applicableNames(parameters: Seq[ScParameter],
                                    substitutor: ScSubstitutor,
                                    nameToArgument: Map[String, Argument]) = for {
    parameter <- parameters

    name = parameter.name
    if name != null

    argument <- nameToArgument.get(name)

    parameterType = substitutor(parameter.`type`().getOrAny)
    if argument.`type`().getOrAny.conforms(parameterType)
  } yield name

  private[this] abstract class ExpressionListInsertHandler extends InsertHandler[LookupElement] {

    override final def handleInsert(context: InsertionContext,
                                    element: LookupElement): Unit = context.getCompletionChar match {
      case ')' =>
      case _ =>
        val element = context
          .getFile
          .findElementAt(context.getStartOffset)

        findArgumentsList(element) match {
          case null =>
          case list => onExpressionList(list)(context)
        }
    }

    protected def onExpressionList(list: ScArgumentExprList)
                                  (implicit context: InsertionContext): Unit
  }

  private[this] final class AssignmentsInsertHandler extends ExpressionListInsertHandler {

    override protected def onExpressionList(list: ScArgumentExprList)
                                           (implicit context: InsertionContext): Unit = {
      foreachArgument(list) { argument =>
        val replacementText = argument.getText + AssignmentText + NotImplementedError
        argument.replaceExpression(
          createExpressionFromText(replacementText)(argument),
          removeParenthesis = false
        )
      }

      val newList = forcePsiPostprocessAndRestoreElement(list)
      createTemplateBuilder(newList)
        .run(context.getEditor, false)
    }

    private def foreachArgument(list: ScArgumentExprList)
                               (action: ScExpression => Unit): Unit =
      list.exprs.foreach(action)

    private def createTemplateBuilder(list: ScArgumentExprList) = {
      val result = TemplateBuilderFactory
        .getInstance
        .createTemplateBuilder(list)

      foreachArgument(list) {
        case ScAssignment(_, Some(placeholder)) =>
          result.replaceElement(
            placeholder,
            NotImplementedError
          )
      }

      result
    }
  }

  private[this] def findArgumentsList(position: PsiElement) =
    getContextOfType(position, classOf[ScArgumentExprList])

  private[this] object ClassConstructor {

    def unapply(`class`: ScClass): Option[ScPrimaryConstructor] = `class`.constructor
  }

  private[this] implicit class LookupElementBuilderExt(private val builder: LookupElementBuilder) extends AnyVal {

    import LookupElementBuilderExt._

    def withCompositeIcon(first: PsiElement,
                          second: PsiElement): LookupElementBuilder =
      builder.withIcon {
        compositeIcon(first, second)
      }

    def withMoveCaretInsertionHandler: LookupElementBuilder =
      builder.withInsertHandler {
        new MoveCaretInsertHandler
      }

    def withSuperMethodParameters(hasSuperQualifier: Boolean): LookupElementBuilder =
      if (hasSuperQualifier)
        builder.withBooleanUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS)
      else
        builder
  }

  private[this] object LookupElementBuilderExt {

    private final class MoveCaretInsertHandler extends ExpressionListInsertHandler {

      override protected def onExpressionList(list: ScArgumentExprList)
                                             (implicit context: InsertionContext): Unit =
        context
          .getEditor
          .getCaretModel
          .moveToOffset(list.getTextRange.getEndOffset) // put caret after )
    }

    import language.implicitConversions

    private implicit def element2Icon(element: PsiElement): Icon =
      element.getIcon(0)

    private def compositeIcon(leftIcon: Icon,
                              rightIcon: Icon) = {
      val result = new LayeredIcon(2)
      result.setIcon(
        rightIcon,
        0,
        2 * leftIcon.getIconWidth / 5,
        0
      )
      result.setIcon(
        leftIcon,
        1
      )
      result
    }
  }

}
