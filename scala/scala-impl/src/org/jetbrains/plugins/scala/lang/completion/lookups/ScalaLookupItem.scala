package org.jetbrains.plugins.scala
package lang
package completion
package lookups

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil._
import org.jetbrains.plugins.scala.annotator.intention._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.{ScalaImportingInsertHandler, ScalaInsertHandler}
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{PresentationUtil, ScImportsHolder}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.escapeKeyword
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.HashBuilder._
import org.jetbrains.plugins.scala.util.UIFreezingGuard

import scala.annotation.tailrec

/**
 * @author Alefas
 * @since 22.03.12
 */
final class ScalaLookupItem private(override val getPsiElement: PsiNamedElement,
                                    override val getLookupString: String,
                                    private[completion] val containingClass: PsiClass)
  extends LookupItem[PsiNamedElement](getPsiElement, getLookupString) {

  import ScalaInsertHandler._
  import ScalaLookupItem._

  def this(element: PsiNamedElement,
           name: String,
           maybeContainingClass: Option[PsiClass] = None) = this(
    element,
    name match {
      case ScalaKeyword.THIS => name
      case _ => escapeKeyword(name)
    },
    maybeContainingClass.orElse(element.containingClassOfNameContext).orNull
  )

  var isClassName: Boolean = false
  var isRenamed: Option[String] = None
  var isAssignment: Boolean = false
  var substitutor: ScSubstitutor = ScSubstitutor.empty
  var shouldImport: Boolean = false
  var isOverloadedForClassName: Boolean = false
  var isNamedParameter: Boolean = false
  var isUnderlined: Boolean = false
  var isInImport: Boolean = false
  var isInStableCodeReference: Boolean = false
  var elementToImport: Option[(ScFunction, ScObject)] = None
  var someSmartCompletion: Boolean = false
  var bold: Boolean = false
  var etaExpanded: Boolean = false
  var prefixCompletion: Boolean = false
  var isLocalVariable: Boolean = false
  var isSbtLookupItem: Boolean = false
  var isInSimpleString: Boolean = false
  var isInSimpleStringNoBraces: Boolean = false
  var isInInterpolatedString: Boolean = false

  def isNamedParameterOrAssignment: Boolean = isNamedParameter || isAssignment

  override def equals(o: Any): Boolean = super.equals(o) &&
    (o match {
      case s: ScalaLookupItem => isNamedParameter == s.isNamedParameter && containingClass == s.containingClass
      case _ => true
    })

  override def hashCode(): Int =
    super.hashCode() #+ isNamedParameter #+ containingClass

  // todo define private
  val containingClassName: String = containingClass match {
    case null => null
    case clazz => clazz.name
  }

  override def renderElement(presentation: LookupElementPresentation): Unit = {
    val grayed = getPsiElement match {
      case _: PsiPackage | _: PsiClass => true
      case _ => false
    }

    presentation.setTailText(
      if (isNamedParameter) AssignmentText else tailText,
      grayed
    )
    presentation.setTypeText(typeText)
    presentation.setIcon(getPsiElement)

    val itemText =
      if (isRenamed.nonEmpty)
        s"$getLookupString <= ${getPsiElement.name}"
      else if (isClassName && shouldImport && containingClassName != null)
        s"$containingClassName.$getLookupString"
      else getLookupString

    presentation.setItemText(itemText)
    wrapOptionIfNeeded(presentation)

    presentation.setStrikeout(getPsiElement)

    presentation.setItemTextBold(bold)
    if (ScalaProjectSettings.getInstance(getPsiElement.getProject).isShowImplisitConversions) {
      presentation.setItemTextUnderlined(isUnderlined)
    }
  }

  private[lookups] def wrapOptionIfNeeded(presentation: LookupElementPresentation): Unit =
    if (someSmartCompletion) {
      presentation.setItemText("Some(" + presentation.getItemText + ")")
    }

  private[lookups] def shiftOptionIfNeeded: Int =
    if (someSmartCompletion) 5 else 0

  private lazy val typeText: String = {
    UIFreezingGuard.withDefaultValue("") {
      implicit val pc: Project = getPsiElement.getProject
      implicit val tpc: TypePresentationContext = TypePresentationContext(getPsiElement)
      import PresentationUtil.{presentationStringForJavaType, presentationStringForScalaType}
      getPsiElement match {
        case fun: ScFunction =>
          val scType = if (!etaExpanded) fun.returnType.getOrAny else fun.`type`().getOrAny
          presentationStringForScalaType(scType, substitutor)
        case fun: ScFun =>
          presentationStringForScalaType(fun.retType, substitutor)
        case alias: ScTypeAliasDefinition =>
          presentationStringForScalaType(alias.aliasedType.getOrAny, substitutor)
        case param: ScParameter =>
          presentationStringForScalaType(param.getRealParameterType.getOrAny, substitutor)
        case t: ScTemplateDefinition if getLookupString == "this" || getLookupString.endsWith(".this") =>
          t.getTypeWithProjections(thisProjections = true) match {
            case Right(tp) =>
              tp.presentableText(t)
            case _ => ""
          }
        case f: PsiField =>
          presentationStringForJavaType(f.getType, substitutor)
        case m: PsiMethod =>
          presentationStringForJavaType(m.getReturnType, substitutor)
        case t: Typeable =>
          presentationStringForScalaType(t.`type`().getOrAny, substitutor)
        case _ => ""
      }
    }
  }

  private lazy val tailText: String = {
    UIFreezingGuard.withDefaultValue("") {
      implicit val pc: Project = getPsiElement.getProject
      implicit val tpc: TypePresentationContext = TypePresentationContext(getPsiElement)
      getPsiElement match {
        //scala
        case _: ScReferencePattern => // todo should be a ScValueOrVariable instance
          containingClassText
        case fun: ScFunction =>
          if (etaExpanded)
            " _"
          else if (isAssignment)
            AssignmentText +
              PresentationUtil.presentationStringForPsiElement(fun.parameterList, substitutor)
          else
            typeParametersText(fun) +
              parametersText(fun.parameterList) +
              containingClassText
        case fun: ScFun =>
          val paramClausesText = fun.paramClauses.map { clause =>
            clause.map {
              PresentationUtil.presentationStringForParameter(_, substitutor)
            }.commaSeparated(Model.Parentheses)
          }.mkString

          typeParametersText(fun.typeParameters) + paramClausesText
        case clazz: PsiClass =>
          typeParametersText(clazz) +
            classLocationSuffix(clazz)
        case method: PsiMethod =>
          typeParametersText(method) +
            (if (isParameterless(method)) "" else parametersText(method.getParameterList)) +
            containingClassText
        case p: PsiPackage =>
          s"    (${p.getQualifiedName})"
        case _ => ""
      }
    }
  }

  private def typeParametersText(typeParameters: Seq[_ <: PsiTypeParameter])
                                (implicit project: Project,
                                 context: TypePresentationContext): String =
    if (typeParameters.isEmpty)
      ""
    else
      typeParameters.map { typeParameter =>
        val substitutor = typeParameter match {
          case _: ScTypeParam => this.substitutor
          case _ => ScSubstitutor.empty
        }
        PresentationUtil.presentationStringForPsiElement(typeParameter, substitutor)
      }.commaSeparated(Model.SquareBrackets)

  private def typeParametersText(owner: PsiTypeParameterListOwner)
                                (implicit project: Project,
                                 context: TypePresentationContext): String = owner match {
    case owner: ScTypeParametersOwner =>
      owner.typeParametersClause.fold("") {
        PresentationUtil.presentationStringForPsiElement(_, substitutor)
      }
    case owner =>
      typeParametersText(owner.getTypeParameters)
  }

  private def parametersText(parametersList: PsiParameterList)
                            (implicit project: Project,
                             context: TypePresentationContext) =
    if (isOverloadedForClassName)
      "(...)"
    else
      PresentationUtil.presentationStringForPsiElement(parametersList, substitutor)

  private def containingClassText =
    if (isClassName && containingClassName != null)
      (if (shouldImport) "" else " in " + containingClassName) +
        classLocationSuffix(containingClass)
    else
      ""

  override def handleInsert(context: InsertionContext): Unit = {
    if (getInsertHandler != null) super.handleInsert(context)
    else if (isClassName || prefixCompletion) {
      context.commitDocument()

      getPsiElement match {
        case element@(_: PsiClass |
                      _: ScTypeAlias |
                      _: ScPackage) =>
          if (isRenamed.isDefined) return

          if (isInSimpleString) {
            new StringInsertPreHandler().handleInsert(context, this)

            new StringInsertPostHandler().handleInsert(context, this)
          }

          var ref = findReferenceAtOffset(context)
          if (ref == null) return

          // todo: is it basically this.isInImport && ...
          val isInImport = getParentOfType(ref, classOf[ScImportStmt]) != null &&
            getParentOfType(ref, classOf[ScImportSelectors]) == null //do not complete in sel

          ref = findQualifier(ref)

          val cl = element match {
            case clazz: PsiClass => ClassToImport(clazz)
            case ta: ScTypeAlias => TypeAliasToImport(ta)
            case pack: ScPackage => PrefixPackageToImport(pack)
          }

          def nameToUse(qualifiedName: String = cl.qualifiedName) = ref match {
            case _: ScDocResolvableCodeReference => qualifiedName
            case _ if isInImport => qualifiedName
            case _ => cl.name
          }

          val referenceText = if (prefixCompletion) {
            val qualifiedName = cl.qualifiedName
            val parts = qualifiedName.split('.')
            val last = parts.length - 1

            if (last >= 0)
              parts(last - 1) + "." + parts(last)
            else
              nameToUse(qualifiedName)
          } else
            nameToUse()

          replaceReference(ref, referenceText)(element)()

          element match {
            case _: ScObject if isInStableCodeReference =>
              context.scheduleAutoPopup()
            case _ =>
          }
        case p: PsiPackage =>
          new ScalaImportingInsertHandler(null) {

            override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
              ScImportsHolder(reference).addImportForPath(p.getQualifiedName)
          }.handleInsert(context, this)
        case _ if containingClass != null =>
          new ScalaImportingInsertHandler(containingClass) {

            override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
              replaceReference(reference)

            override protected def qualifyOnly(reference: ScReferenceExpression): Unit = {}
          }.handleInsert(context, this)
        case _ =>
      }
    } else {
      new ScalaInsertHandler().handleInsert(context, this)
    }
  }

  private[completion] def findReferenceAtOffset(context: InsertionContext) = findElementOfClassAtOffset(
    context.getFile,
    realStartOffset(context),
    classOf[ScReference],
    false
  )

  private def realStartOffset(context: InsertionContext): Int = {
    val simpleStringAdd =
      if (isInSimpleString && isInSimpleStringNoBraces) 1
      else if (isInSimpleString) 2
      else if (isInInterpolatedString) 1
      else 0

    context.getStartOffset + shiftOptionIfNeeded + simpleStringAdd
  }
}

object ScalaLookupItem {

  @tailrec
  def delegate(element: LookupElement): LookupElement = element match {
    case decorator: LookupElementDecorator[_] => delegate(decorator.getDelegate)
    case _ => element
  }

  def unapply(element: LookupElement): Option[(ScalaLookupItem, PsiNamedElement)] =
    delegate(element) match {
      case item: ScalaLookupItem => Some(item, item.getPsiElement)
      case _ => None
    }

  @tailrec
  private def findQualifier(reference: ScReference): ScReference = reference.getParent match {
    case parent: ScReference if !parent.qualifier.contains(reference) => findQualifier(parent)
    case _ => reference
  }

  private def classLocationSuffix(`class`: PsiClass) = {
    val location = `class`.getPresentation match {
      case null => null
      case presentation => presentation.getLocationString
    }

    location match {
      case null | "" => ""
      case _ => " " + location
    }
  }
}
