package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import com.intellij.psi.{PsiElement, PsiMember, PsiMethod}
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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createExpressionWithContextFromText}
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
    ).afterLeaf("(", ",")
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
          val argumentToStart = new ArgumentToStart(argumentsList)()

          createFunctionArgumentsElements(
            reference,
            argumentToStart,
            reference.qualifier.exists(_.isInstanceOf[ScSuperReference]),
            parameters.getInvocationCount
          ) ++ createAssignmentElements(reference, argumentToStart)
        case _ => Iterable.empty
      }
    }

    private def createFunctionArgumentsElements(reference: ScReferenceExpression,
                                                argumentToStart: ArgumentToStart,
                                                hasSuperQualifier: Boolean,
                                                invocationCount: Int) = for {
      ScalaResolveResult(method: ScMethodLike, substitutor) <- reference.completionVariants()
      if method.name == reference.refName

      lookupElement <- createLookupElement(method, argumentToStart, substitutor) {
        findResolvableParameters(reference, invocationCount)
      }
    } yield lookupElement
      .withMoveCaretInsertionHandler
      .withSuperMethodParameters(hasSuperQualifier)

    private def createAssignmentElements(reference: ScReferenceExpression,
                                         argumentToStart: ArgumentToStart) = for {
      ScalaResolveResult(function: ScFunction, substitutor) <- reference.multiResolveScala(incomplete = true).toSeq
      if function.isApplyMethod

      lookupElement <- createLookupElement(function, argumentToStart, substitutor) {
        findMethodParameters(function)
      }
    } yield lookupElement
      .withTailText(AssignmentText)
      .withInsertHandler(new AssignmentsInsertHandler)

    private def findResolvableParameters(reference: PsiElement,
                                         invocationCount: Int)
                                        (parameters: collection.Seq[ScParameter]) = for {
      parameter <- parameters
      name = parameter.name

      expression = createExpressionWithContextFromText(
        name,
        reference.getContext,
        reference
      ).asInstanceOf[ScReferenceExpression]

      iconable <- expression.resolve match {
        case method: PsiMethod if method.isConstructor || !method.isParameterless => None
        case member: PsiMember if !isAccessible(member, invocationCount)(reference) => None
        case iconable => Option(iconable)
      }
    } yield name -> ExpressionArgument(expression, iconable)
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
                  val argumentToStart = new ArgumentToStart(argumentsList)(constructorInvocation.arguments.indexOf(argumentsList))

                  for {
                    extractedClassConstructor <- clazz.constructors

                    lookupElement <- createLookupElement(extractedClassConstructor, argumentToStart, substitutor) {
                      findMethodParameters(constructor)
                    }
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

  private[this] final class ArgumentToStart private(clauseIndex: Int,
                                                    argumentsToDrop: Int) {

    def this(list: ScArgumentExprList)
            (clauseIndex: Int = list.invocationCount - 1) = this(
      clauseIndex,
      list.children.count(_.textMatches(","))

      /** empty expressions cannot be handled via [[ScArgumentExprList.exprs]] */
    )

    def parametersNames(method: ScMethodLike): collection.Seq[ScParameter] = method
      .parametersInClause(clauseIndex)
      .drop(argumentsToDrop)
  }

  private[this] sealed abstract class Argument(protected val typeable: Typeable,
                                               protected val iconable: PsiElement) {

    final def conformsTo(parameter: ScParameter,
                         substitutor: ScSubstitutor): Boolean = {
      val parameterType = substitutor(parameter.`type`().getOrAny)
      typeable.`type`().getOrAny.conforms(parameterType)
    }

    final def icon: Icon = iconable.getIcon(0)
  }

  private[this] final case class ExpressionArgument(override protected val typeable: ScReferenceExpression,
                                                    override protected val iconable: PsiElement)
    extends Argument(typeable, iconable)

  private[this] final case class ParameterArgument(override protected val typeable: ScParameter)
    extends Argument(typeable, typeable)

  private[this] def findMethodParameters(method: ScMethodLike) = { _: collection.Seq[ScParameter] =>
    method
      .parameterList
      .params
      .map { parameter =>
        parameter.name -> ParameterArgument(parameter)
      }
  }

  private[this] def createLookupElement(method: ScMethodLike,
                                        argumentToStart: ArgumentToStart,
                                        substitutor: ScSubstitutor)
                                       (argumentsWithNames: collection.Seq[ScParameter] => collection.Seq[(String, Argument)]) = {
    val parameters = argumentToStart.parametersNames(method)

    parameters.length match {
      case 0 | 1 => None
      case clauseLength =>
        val nameToArgument = argumentsWithNames(parameters).toMap

        applicableNames(parameters, substitutor, nameToArgument) match {
          case names if names.length == clauseLength =>
            val Seq(leftIcon, rightIcon) = names
              .take(2)
              .map(nameToArgument)
              .map(_.icon)

            Some {
              LookupElementBuilder
                .create(names.commaSeparated())
                .withIcon(compositeIcon(leftIcon, rightIcon))
            }
          case _ => None
        }
    }
  }

  private[this] def applicableNames(parameters: collection.Seq[ScParameter],
                                    substitutor: ScSubstitutor,
                                    nameToArgument: Map[String, Argument]) = for {
    parameter <- parameters

    name = parameter.name
    if name != null

    argument <- nameToArgument.get(name)
    if argument.conformsTo(parameter, substitutor)
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
                               (action: ScExpression => Unit)
                               (implicit context: InsertionContext): Unit =
      list
        .exprs
        .filterNot(_.getTextRange.getEndOffset < context.getStartOffset)
        .foreach(action)

    private def createTemplateBuilder(list: ScArgumentExprList)
                                     (implicit context: InsertionContext) = {
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
  }

  private[this] def compositeIcon(leftIcon: Icon,
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
