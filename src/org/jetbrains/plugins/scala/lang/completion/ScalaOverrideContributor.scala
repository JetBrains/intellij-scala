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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.overrideImplement._
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
  * Created by kate
  * on 3/1/16
  * contibute override/implement elements. May be called on override keyword (ove<caret>)
  * or after override/implement element definition (override def <caret>)
  * or on method/field/type name (without override) -> this will add override keyword if there is appropriate setting
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

  extend(CompletionType.BASIC, PlatformPatterns.psiElement.
    and(new FilterPattern(new AndFilter(new NotFilter(new LeftNeighbour(new TextContainFilter(".")))))), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
      def checkIfElementIsAvailable(element: PsiElement, clazz: ScTemplateDefinition): Boolean = {
        clazz.members.contains(element)
      }

      val position = positionFromParameters(parameters)
      val clazz = PsiTreeUtil.getContextOfType(position, classOf[ScTemplateDefinition])
      if (clazz == null) return

      Option(PsiTreeUtil.getParentOfType(position, classOf[ScModifierListOwner])).foreach {
        case _: ScParameter => return
        case mlo =>
          if (!checkIfElementIsAvailable(mlo, clazz)) return
          val hasOverride = if (!mlo.hasModifierProperty("override")) false else true
          addCompletionsAfterOverride(resultSet, position, clazz, hasOverride)
      }
    }
  })

  class MyElementRenderer(member: ScalaNamedMember) extends LookupElementRenderer[LookupElementDecorator[LookupElement]] {
    def renderElement(element: LookupElementDecorator[LookupElement], presentation: LookupElementPresentation): Unit = {
      element.getDelegate.renderElement(presentation)

      val (resultText, tailText) = member match {
        case mm: ScMethodMember =>
          (mm.text + " = {...}", mm.scType.presentableText)
        case mVal: ScValueMember =>
          (mVal.name, mVal.scType.presentableText)
        case mVar: ScVariableMember =>
          (mVar.name, mVar.scType.presentableText)
        case ta: ScAliasMember =>
          val aliasType = ta.getElement match {
            case tad: ScTypeAliasDefinition =>
              tad.aliasedTypeElement.calcType.presentableText
            case _ => ""
          }
          (ta.name, aliasType)
      }

      presentation.setTypeText(tailText)
      presentation.setItemText("override " + resultText)
    }
  }

  private def addCompletionsOnOverrideKeyWord(resultSet: CompletionResultSet, parameters: CompletionParameters): Unit = {
    val position = positionFromParameters(parameters)

    Option(PsiTreeUtil.getContextOfType(position, classOf[ScTemplateDefinition], classOf[ScParameter])).foreach {
      case _: ScParameter =>
      case clazz: ScTemplateDefinition =>
        val classMembers = getMembers(clazz)
        if (classMembers.isEmpty) return

        handleMembers(classMembers, clazz,
          (classMember, clazz) => createText(classMember, clazz, full = true), resultSet) { classMember =>
          new MyInsertHandler()
        }
    }
  }

  private def addCompletionsAfterOverride(resultSet: CompletionResultSet, position: PsiElement,
                                          clazz: ScTemplateDefinition, hasOverride: Boolean): Unit = {

    val classMembers = getMembers(clazz)
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
      new MyInsertHandler(hasOverride)
    }
  }

  private def getMembers(templateDefinition: ScTemplateDefinition): Iterable[ClassMember] = {
    ScalaOIUtil.getMembersToOverride(templateDefinition, withSelfType = true) ++
      ScalaOIUtil.getMembersToImplement(templateDefinition, withSelfType = true)
  }

  class MyInsertHandler(hasOverride: Boolean = false) extends InsertHandler[LookupElement] {
    def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      def makeInsertion(): Unit = {
        val elementOption = Option(PsiTreeUtil.getContextOfType(context.getFile.findElementAt(context.getStartOffset),
          classOf[ScModifierListOwner]))

        elementOption.foreach { element =>
          TypeAdjuster.markToAdjust(element)
          if (!hasOverride && ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED) {
            element.setModifierProperty("override", value = true)
          }
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
        val mBody = if (mm.isOverride) ScalaGenerationInfo.getMethodBody(mm, td, isImplement = false) else "???"
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

    if (!full) text.indexOf(" ", 1) match {
      //remove val, var, def or type
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

