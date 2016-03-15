package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.util.Iconable._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.psi.filters._
import com.intellij.psi.filters.position.{FilterPattern, LeftNeighbour}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers.ModifiersFilter
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.overrideImplement._

/**
  * Created by kate
  * on 3/1/16
  * contibute override elements. May be called on override keyword (ove<caret>)
  * or after override element definition (override def <caret>)
  */
class ScalaOverrideContributor extends ScalaCompletionContributor {
  private def registerOverrideCompletion(filter: ElementFilter, keyword: String) {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement.
      and(new FilterPattern(new AndFilter(new NotFilter(new LeftNeighbour(new TextContainFilter("override"))),
        new AndFilter(new NotFilter(new LeftNeighbour(new TextFilter("."))), filter)))),
      new CompletionProvider[CompletionParameters] {
        def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
          addCompletionsOnOverrideKeyWord(resultSet, parameters)
        }
      })
  }

  extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
      addCompletionsAfterOverride(resultSet, parameters)
    }
  })

  class MyElementRenderer(member: ScalaNamedMember) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {
    def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation) = {
      element.getDelegate.renderElement(presentation)

      val (resultText, tailText) = member match {
        case mm: ScMethodMember =>
          (mm.text + " = {...}", mm.scType.presentableText)
        case mVal: ScValueMember =>
          (mVal.getText, mVal.scType.presentableText)
        case mVar: ScVariableMember =>
          (mVar.getText, mVar.scType.presentableText)
        case ta: ScAliasMember =>
          val aliasType = ta.getElement match {
            case tad: ScTypeAliasDefinition =>
              tad.aliasedTypeElement.calcType.presentableText
            case _ => ""
          }
          (ta.getText, aliasType)
      }

      presentation.setTypeText(tailText)
      presentation.setItemText(resultText)
    }
  }

  private def addCompletionsOnOverrideKeyWord(resultSet: CompletionResultSet, parameters: CompletionParameters): Unit = {
    val position = positionFromParameters(parameters)

    val clazz = PsiTreeUtil.getParentOfType(position, classOf[ScTemplateDefinition], false)
    if (clazz == null) return

    val classMembers = ScalaOIUtil.getMembersToOverride(clazz, withSelfType = true)
    if (classMembers.isEmpty) return

    handleMembers(classMembers, clazz, (classMember, clazz) => createText(classMember, clazz, full = true), resultSet) { classMember =>
      new MyInsertHandler()
    }
  }

  private def addCompletionsAfterOverride(resultSet: CompletionResultSet, parameters: CompletionParameters): Unit = {
    val position = positionFromParameters(parameters)

    val clazz = PsiTreeUtil.getParentOfType(position, classOf[ScTemplateDefinition], /*strict = */ false)
    if (clazz == null) return

    val mlo = Option(PsiTreeUtil.getContextOfType(position, classOf[ScModifierListOwner]))
    if (mlo.isEmpty) return
    else if (mlo.isDefined && !mlo.get.hasModifierProperty("override")) return

    val classMembers = ScalaOIUtil.getMembersToOverride(clazz, withSelfType = true)
    if (classMembers.isEmpty) return

    def membersToRender = position.getContext match {
      case m: PsiMethod => classMembers.filter(_.isInstanceOf[ScMethodMember])
      case typedDefinition: ScTypedDefinition if typedDefinition.isVal =>
        classMembers.filter(_.isInstanceOf[ScValueMember])
      case typedDefinition: ScTypedDefinition if typedDefinition.isVar =>
        classMembers.filter(_.isInstanceOf[ScVariableMember])
      case typeAlis: ScTypeAlias => classMembers.filter(_.isInstanceOf[ScAliasMember])
      case _ => classMembers
    }

    handleMembers(membersToRender, clazz, (classMember, clazz) => createText(classMember, clazz), resultSet) { classMember =>
      new MyInsertHandler()
    }
  }

  class MyInsertHandler() extends InsertHandler[LookupElement] {
    def handleInsert(context: InsertionContext, item: LookupElement) = {
      def makeInsertion(): Unit = {
        val elementOption = Option(PsiTreeUtil.getContextOfType(context.getFile.findElementAt(context.getStartOffset),
          classOf[ScModifierListOwner]))

        elementOption.foreach { element =>
          TypeAdjuster.markToAdjust(element)
          ScalaGenerationInfo.positionCaret(context.getEditor, element.asInstanceOf[PsiMember])
          context.commitDocument()
        }
      }

      makeInsertion()
    }
  }

  private def createText(classMember: ClassMember, td: ScTemplateDefinition, full: Boolean = false): String = {
    val needsInferType = ScalaGenerationInfo.needsInferType
    val text: String = classMember match {
      case mm: ScMethodMember =>
        val mBody = ScalaGenerationInfo.getMethodBody(mm, td, isImplement = false)
        val fun = if (full)
          ScalaPsiElementFactory.createOverrideImplementMethod(mm.sign, mm.getElement.getManager,
            needsOverrideModifier = false, needsInferType = needsInferType: Boolean, mBody)
        else ScalaPsiElementFactory.createMethodFromSignature(mm.sign, mm.getElement.getManager,
          needsInferType = needsInferType, mBody)
        fun.getText
      case tm: ScAliasMember =>
        ScalaPsiElementFactory.getOverrideImplementTypeSign(tm.getElement,
          tm.substitutor, "this.type", needsOverride = false)
      case value: ScValueMember =>
        ScalaPsiElementFactory.getOverrideImplementVariableSign(value.element, value.substitutor, "_",
          needsOverride = false, isVal = true, needsInferType = needsInferType)
      case variable: ScVariableMember =>
        ScalaPsiElementFactory.getOverrideImplementVariableSign(variable.element, variable.substitutor, "_",
          needsOverride = false, isVal = false, needsInferType = needsInferType)
      case _ => " "
    }

    if (!full) text.indexOf(" ", 1) match { //remove val, var, def or type
      case -1 => text
      case part => text.substring(part + 1)
    } else "override " + text
  }

  private def handleMembers(classMembers: Iterable[ClassMember], td: ScTemplateDefinition,
                            name: (ClassMember, ScTemplateDefinition) => String,
                            resultSet: CompletionResultSet)
                           (insertionHandler: ClassMember => InsertHandler[LookupElement]): Unit = {
    classMembers.foreach {
      case mm: ScalaNamedMember =>
        val lookupItem = LookupElementBuilder.create(mm.getElement, name(mm, td))
          .withIcon(mm.getPsiElement.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS))
          .withInsertHandler(insertionHandler(mm))

        val renderingDecorator = LookupElementDecorator.withRenderer(lookupItem, new MyElementRenderer(mm))
        resultSet.consume(renderingDecorator)
      case _ =>
    }
  }

  registerOverrideCompletion(new ModifiersFilter, "override")
}

