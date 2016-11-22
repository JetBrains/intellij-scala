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
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, TypeAdjuster}
import org.jetbrains.plugins.scala.overrideImplement._
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

/**
  * Created by kate
  * on 3/1/16
  * contribute override/implement elements. May be called on override keyword (ove<caret>)
  * or after override/implement element definition (override def <caret>)
  * or on method/field/type name (without override) -> this will add override keyword if there is appropriate setting
  */
class ScalaOverrideContributor extends ScalaCompletionContributor {
  private def registerOverrideCompletion(filter: ElementFilter): Unit = {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
      and(new FilterPattern(new AndFilter(new NotFilter(new LeftNeighbour(new TextContainFilter("override"))),
        new AndFilter(new NotFilter(new LeftNeighbour(new TextFilter("."))), filter)))),
      new CompletionProvider[CompletionParameters] {
        def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
          addCompletionsOnOverrideKeyWord(resultSet, parameters)
        }
      })
  }

  /**
    * handle only declarations here
    */
  extend(CompletionType.BASIC, PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).
    and(new FilterPattern(new AndFilter(new NotFilter(new LeftNeighbour(new TextContainFilter(".")))))), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
      val position = positionFromParameters(parameters)

      Option(PsiTreeUtil.getContextOfType(position, classOf[ScDeclaration]))
        .collect { case ml: ScModifierListOwner => ml }.foreach { declaration =>
        Option(PsiTreeUtil.getContextOfType(declaration, classOf[ScTemplateDefinition])).foreach { clazz =>
          addCompletionsAfterOverride(resultSet, declaration, clazz)
        }
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
          val aliasType = Option(ta.getElement).collect {
            case definition: ScTypeAliasDefinition => definition
          }.flatMap {
            _.aliasedTypeElement
          }.map {
            _.calcType
          }.map {
            _.presentableText
          }.getOrElse("")
          (ta.name, aliasType)
      }

      presentation.setTypeText(tailText)
      presentation.setItemText("override " + resultText)
    }
  }

  //one word (simple completion throw generation all possible variatns)
  private def addCompletionsOnOverrideKeyWord(resultSet: CompletionResultSet, parameters: CompletionParameters): Unit = {
    val position = positionFromParameters(parameters)
    val body = Option(position.getContext.getContext)

    body.collect { case body: ScTemplateBody => body }.foreach { body =>
      val clazz = ScalaPsiUtil.getContextOfType(body, true, classOf[ScTemplateDefinition]).asInstanceOf[ScTemplateDefinition]
      val classMembers = getMembers(clazz)
      if (classMembers.isEmpty) return

      handleMembers(classMembers, clazz,
        (classMember, clazz) => createText(classMember, clazz, full = true), resultSet) { _ => new MyInsertHandler() }
    }
  }

  private def addCompletionsAfterOverride(resultSet: CompletionResultSet, member: ScModifierListOwner, clazz: ScTemplateDefinition): Unit = {

    val classMembers = getMembers(clazz)
    if (classMembers.isEmpty) return

    def membersToRender = member match {
      case _: PsiMethod => classMembers.filter(_.isInstanceOf[ScMethodMember])
      case typedDefinition: ScTypedDefinition if typedDefinition.isVal =>
        classMembers.filter(_.isInstanceOf[ScValueMember])
      case typedDefinition: ScTypedDefinition if typedDefinition.isVar =>
        classMembers.filter(_.isInstanceOf[ScVariableMember])
      case _: ScTypeAlias => classMembers.filter(_.isInstanceOf[ScAliasMember])
      case _ => classMembers
    }

    handleMembers(membersToRender, clazz, (classMember, clazz) => createText(classMember, clazz), resultSet) { _ =>
      new MyInsertHandler(member.hasModifierProperty("override"))
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
          if (!hasOverride && !element.hasModifierProperty("override")) {
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
    ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY =
      ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE

    implicit val manager = classMember.getElement.getManager
    val text: String = classMember match {
      case mm: ScMethodMember =>
        val mBody = if (mm.isOverride) ScalaGenerationInfo.getMethodBody(mm, td, isImplement = false) else "???"
        val fun =
          if (full)
            createOverrideImplementMethod(mm.sign, needsOverrideModifier = true, mBody, withComment = false, withAnnotation = false)
          else
            createMethodFromSignature(mm.sign, needsInferType = true, mBody, withComment = false, withAnnotation = false)

        TypeAnnotationUtil.removeTypeAnnotationIfNeeded(fun)
        fun.getText
      case tm: ScAliasMember =>
        getOverrideImplementTypeSign(tm.getElement, tm.substitutor, needsOverride = false)
      case member: ScValueMember =>
        val variable = createOverrideImplementVariable(member.element, member.substitutor, needsOverrideModifier = false, isVal = false)
        TypeAnnotationUtil.removeTypeAnnotationIfNeeded(variable)
        variable.getText
      case member: ScVariableMember =>
        val variable = createOverrideImplementVariable(member.element, member.substitutor, needsOverrideModifier = false, isVal = false)
        TypeAnnotationUtil.removeTypeAnnotationIfNeeded(variable)
        variable.getText
      case _ => " "
    }

    if (!full) text.indexOf(" ", 1) match {
      //remove val, var, def or type
      case -1 => text
      case part => text.substring(part + 1)
    } else if (classMember.isInstanceOf[ScMethodMember]) text else "override " + text
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

  registerOverrideCompletion(new ModifiersFilter)
}
