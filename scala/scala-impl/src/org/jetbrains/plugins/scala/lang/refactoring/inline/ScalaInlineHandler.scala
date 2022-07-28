package org.jetbrains.plugins.scala
package lang
package refactoring
package inline


import com.intellij.lang.refactoring.InlineHandler
import com.intellij.lang.refactoring.InlineHandler.Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Condition
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.util.{CommonRefactoringUtil, RefactoringMessageDialog}
import com.intellij.util.FilteredQuery
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScStableReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.highlightOccurrences
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

class ScalaInlineHandler extends InlineHandler {

  override def removeDefinition(element: PsiElement, settings: InlineHandler.Settings): Unit = {
    def removeElementWithNonSignificantSibilings(value: PsiElement): Unit = {
      val children = new ArrayBuffer[PsiElement]
      var psiElement = value.getNextSibling
      while (psiElement != null && (psiElement.getNode.getElementType == ScalaTokenTypes.tSEMICOLON || psiElement.getText.trim == "")) {
        children += psiElement
        psiElement = psiElement.getNextSibling
      }
      for (child <- children) {
        child.getParent.getNode.removeChild(child.getNode)
      }
      value.getParent.getNode.removeChild(value.getNode)
    }

    element match {
      case rp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(rp, classOf[ScDeclaredElementsHolder]) match {
          case v@(_: ScValue | _: ScVariable) if v.declaredElements.length == 1 =>
            removeElementWithNonSignificantSibilings(v)
          case _ =>
        }
      case funDef: ScFunctionDefinition => CodeEditUtil.removeChild(funDef.getParent.getNode, funDef.getNode)
      case typeAlias: ScTypeAliasDefinition =>
        removeElementWithNonSignificantSibilings(typeAlias)
      case _ =>
    }
  }

  override def createInliner(element: PsiElement, settings: InlineHandler.Settings): InlineHandler.Inliner = new ScalaInliner

  override def prepareInlineElement(element: PsiElement, editor: Editor, invokedOnReference: Boolean): InlineHandler.Settings = {
    def title(suffix: String) = "Scala Inline " + suffix

    def showErrorHint(@Nls message: String, titleSuffix: String): InlineHandler.Settings = {
      val inlineTitle = title(titleSuffix)
      CommonRefactoringUtil.showErrorHint(element.getProject, editor, message, inlineTitle, HelpID.INLINE_VARIABLE)
      Settings.CANNOT_INLINE_SETTINGS
    }

    def getSettings(psiNamedElement: PsiNamedElement, inlineTitleSuffix: String, inlineDescriptionSuffix: String): InlineHandler.Settings = {
      val refs = ReferencesSearch.search(psiNamedElement, psiNamedElement.getUseScope).findAll.asScala.toSeq
      val inlineTitle = title(inlineTitleSuffix)
      val occurrenceHighlighters = highlightOccurrences(refs.map(_.getElement))(element.getProject, editor)
      val settings = new InlineHandler.Settings {
        override def isOnlyOneReferenceToInline: Boolean = false
      }
      if (refs.isEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.never.used"), inlineTitleSuffix)
      else if (!psiNamedElement.is[ScTypeAliasDefinition] &&
        refs.map(_.getElement)
          .flatMap(_.nonStrictParentOfType(Seq(classOf[ScStableCodeReference], classOf[ScStableReferencePattern])))
          .nonEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.stable.reference"), inlineTitleSuffix)
      else if (!ApplicationManager.getApplication.isUnitTestMode) {
        val occurences = refs.size match {
          case 1 => "(1 occurrence)"
          case n => s"($n occurrences)"
        }

        val question = s"Inline $inlineDescriptionSuffix ${
          psiNamedElement.name
        }? $occurences"
        val dialog = new RefactoringMessageDialog(
          inlineTitle,
          question,
          HelpID.INLINE_VARIABLE,
          "OptionPane.questionIcon",
          true,
          element.getProject)
        dialog.show()
        if (!dialog.isOK) {
          occurrenceHighlighters.foreach(_.dispose())
          InlineHandler.Settings.CANNOT_INLINE_SETTINGS
        } else settings
      } else settings
    }

    def isSimpleTypeAlias(typeAlias: ScTypeAliasDefinition): Boolean = {
      typeAlias.aliasedTypeElement.toSeq.flatMap {
        _.depthFirst()
      }.forall {
        case t: ScTypeElement =>
          t.calcType match {
            case _: TypeParameterType => false
            case part: ScProjectionType if !ScalaPsiUtil.hasStablePath(part.element) =>
              false
            case _ => true
          }
        case _ => true
      }
    }

    def isParametrizedTypeAlias(typeAlias: ScTypeAliasDefinition) = typeAlias.typeParameters.nonEmpty

    Stats.trigger(FeatureKey.inline)

    implicit val project: ProjectContext = element.projectContext

    def isFunctionalType(typedDef: ScTypedDefinition) =
      FunctionType.unapply(typedDef.`type`().getOrAny).exists(_._2.nonEmpty) &&
        (typedDef match {
          case _: ScFunctionDeclaration | _: ScFunctionDefinition => false
          case _ => true
        })

    def fromDifferentFile(named: ScNamedElement) = {
      named.getContainingFile != PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    }

    def errorOrSettingsForFunction(funDef: ScFunctionDefinition): InlineHandler.Settings = {
      if (funDef.recursiveReferences.nonEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.recursive.function"), "method")
      else if (funDef.paramClauses.clauses.size > 1)
        showErrorHint(ScalaBundle.message("cannot.inline.function.multiple.clauses"), "method")
      else if (funDef.paramClauses.clauses.exists(_.isImplicit))
        showErrorHint(ScalaBundle.message("cannot.inline.function.implicit.parameters"), "method")
      else if (funDef.parameters.exists(_.isVarArgs))
        showErrorHint(ScalaBundle.message("cannot.inline.function.varargs"), "method")
      else if (funDef.isSpecial)
        showErrorHint(ScalaBundle.message("cannot.inline.special.function"), "method")
      else if (funDef.typeParameters.nonEmpty)
        showErrorHint(ScalaBundle.message("cannot.inline.generic.function"), "method")
      else if (funDef.parameters.exists(isFunctionalType))
        showErrorHint(ScalaBundle.message("cannot.inline.function.functional.parameters"), "method")
      else if (funDef.parameters.nonEmpty && hasNoCallUsages(funDef))
        showErrorHint(ScalaBundle.message("cannot.inline.not.method.call"), "method")
      else if (funDef.body.isDefined)
        if (funDef.isLocal) getSettings(funDef, "Method", "local method")
        else getSettings(funDef, "Method", "method")
      else null
    }

    element match {
      case _: ScParameter =>
        showErrorHint(ScalaBundle.message("cannot.inline.parameter"), "parameter")
      case typedDef: ScTypedDefinition if isFunctionalType(typedDef) =>
        showErrorHint(ScalaBundle.message("cannot.inline.value.functional.type"), "element")
      case named: ScNamedElement if fromDifferentFile(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.different.files"), "element")
      case named: ScNamedElement if ScalaPsiUtil.isImplicit(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.implicit.element"), "member")
      case named: ScNamedElement if !usedInSameClassOnly(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.used.outside.class"), "member")
      case bp: ScBindingPattern =>
        PsiTreeUtil.getParentOfType(bp, classOf[ScPatternDefinition], classOf[ScVariableDefinition]) match {
          case definition: ScPatternDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "value")
          case definition: ScVariableDefinition if !definition.isSimple =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "variable")
          case parent if parent != null && parent.declaredElements == Seq(element) =>
            if (parent.isLocal) getSettings(parent.declaredElements.head, "Variable", "local variable")
            else getSettings(parent.declaredElements.head, "Variable", "variable")
          case _ =>
            showErrorHint(ScalaBundle.message("cannot.inline.not.simple.pattern"), "pattern")
        }
      case funDef: ScFunctionDefinition =>
        errorOrSettingsForFunction(funDef)
      case typeAlias: ScTypeAliasDefinition if isParametrizedTypeAlias(typeAlias) || !isSimpleTypeAlias(typeAlias) =>
        showErrorHint(ScalaBundle.message("cannot.inline.notsimple.typealias"), "Type Alias")
      case typeAlias: ScTypeAliasDefinition =>
        getSettings(typeAlias, "Type Alias", "type alias")
      case _ => null
    }
  }

  private def usedInSameClassOnly(named: ScNamedElement): Boolean = {
    ScalaPsiUtil.nameContext(named) match {
      case member: ScMember =>
        val allReferences = ReferencesSearch.search(named, named.getUseScope)
        val notInSameClass: Condition[PsiReference] =
          ref => member.containingClass != null && !PsiTreeUtil.isAncestor(member.containingClass, ref.getElement, true)

        val notInSameClassQuery = new FilteredQuery[PsiReference](allReferences, notInSameClass)
        notInSameClassQuery.findFirst() == null
      case _ => true
    }
  }

  private def hasNoCallUsages(fun: ScFunctionDefinition): Boolean = {
    //we already know that all usages are in the same class
    val scope = new LocalSearchScope(fun.containingClass.toOption.getOrElse(fun.getContainingFile))

    val allReferences = ReferencesSearch.search(fun, scope)
    val notCall: Condition[PsiReference] = ref => !ref.getElement.getParent.is[ScMethodCall]
    val noCallUsages = new FilteredQuery[PsiReference](allReferences, notCall)
    noCallUsages.findFirst() != null
  }
}
