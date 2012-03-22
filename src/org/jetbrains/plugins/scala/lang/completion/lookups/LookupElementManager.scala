package org.jetbrains.plugins.scala.lang.completion.lookups

import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, Nothing, ScType}
import org.jetbrains.plugins.scala.lang.completion.handlers.{ScalaInsertHandler, ScalaClassNameInsertHandler}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext, TypingContextOwner}
import com.intellij.codeInsight.lookup.{LookupElementPresentation, LookupElementRenderer, LookupElementBuilder, LookupElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDefinition, ScFunction, ScFun}
import com.intellij.psi._

/**
 * @author Alefas
 * @since 19.03.12
 */
object LookupElementManager {
  /*def getLookup(resolveResult: ScalaResolveResult,
                qualifierType: ScType = Nothing,
                isClassName: Boolean = false,
                isInImport: Boolean = false,
                isOverloadedForClassName: Boolean = false,
                shouldImport: Boolean = false,
                isInStableCodeReference: Boolean = false): LookupElement = {
    import org.jetbrains.plugins.scala.lang.psi.PresentationUtil.presentationString
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor
    val isRenamed: Option[String] = resolveResult.isRenamed match {
      case Some(x) if element.name != x => Some(x)
      case _ => None
    }

    def getLookupElementInternal(isAssignment: Boolean,
                                 name: String): (LookupElement, PsiNamedElement, ScSubstitutor) = {
      var lookupBuilder: LookupElementBuilder =
        LookupElementBuilder.create(element, name) //don't add elements to lookup
      lookupBuilder = lookupBuilder.setInsertHandler(
        if (isClassName) new ScalaClassNameInsertHandler else new ScalaInsertHandler
      )
      val containingClass = ScalaPsiUtil.nameContext(element) match {
        case memb: PsiMember => memb.getContainingClass
        case _ => null
      }
      var isBold = false
      var isDeprecated = false
      ScType.extractDesignated(qualifierType) match {
        case Some((named, _)) => {
          val clazz: PsiClass = named match {
            case cl: PsiClass => cl
            case tp: TypingContextOwner => tp.getType(TypingContext.empty).map(ScType.extractClass(_)) match {
              case Success(Some(cl), _) => cl
              case _ => null
            }
            case _ => null
          }
          if (clazz != null)
            element match {
              case m: PsiMember => {
                if (m.getContainingClass == clazz) isBold = true
              }
              case _ =>
            }
        }
        case _ =>
      }
      val isUnderlined = resolveResult.implicitFunction != None
      element match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
        case _ =>
      }
      lookupBuilder = lookupBuilder.setRenderer(new LookupElementRenderer[LookupElement] {
        def renderElement(ignore: LookupElement, presentation: LookupElementPresentation) {
          val tailText: String = element match {
            case t: ScFun => {
              if (t.typeParameters.length > 0) t.typeParameters.map(param => presentationString(param, substitutor)).
                mkString("[", ", ", "]")
              else ""
            }
            case t: ScTypeParametersOwner => {
              t.typeParametersClause match {
                case Some(tp) => presentationString(tp, substitutor)
                case None => ""
              }
            }
            case p: PsiTypeParameterListOwner if p.getTypeParameters.length > 0 => {
              p.getTypeParameters.map(ptp => presentationString(ptp)).mkString("[", ", ", "]")
            }
            case _ => ""
          }
          element match {
            //scala
            case fun: ScFunction => {
              presentation.setTypeText(presentationString(fun.returnType.getOrAny, substitutor))
              val tailText1 = if (isAssignment) {
                " = " + presentationString(fun.paramClauses, substitutor)
              } else {
                tailText + (
                  if (!isOverloadedForClassName) presentationString(fun.paramClauses, substitutor)
                  else "(...)"
                  ) + (
                  if (shouldImport && isClassName && containingClass != null)
                    " " + containingClass.getPresentation.getLocationString
                  else if (isClassName && containingClass != null)
                    " in " + containingClass.name + " " + containingClass.getPresentation.getLocationString
                  else ""
                  )
              }
              presentation.setTailText(tailText1)
            }
            case fun: ScFun => {
              presentation.setTypeText(presentationString(fun.retType, substitutor))
              val paramClausesText = fun.paramClauses.map(_.map(presentationString(_, substitutor)).
                mkString("(", ", ", ")")).mkString
              presentation.setTailText(tailText + paramClausesText)
            }
            case bind: ScBindingPattern => {
              presentation.setTypeText(presentationString(bind.getType(TypingContext.empty).getOrAny, substitutor))
            }
            case f: ScFieldId => {
              presentation.setTypeText(presentationString(f.getType(TypingContext.empty).getOrAny, substitutor))
            }
            case param: ScParameter => {
              val str: String =
                presentationString(param.getRealParameterType(TypingContext.empty).getOrAny, substitutor)
              if (resolveResult.isNamedParameter) {
                presentation.setTailText(" = " + str)
              } else {
                presentation.setTypeText(str)
              }
            }
            case clazz: PsiClass => {
              val location: String = clazz.getPresentation.getLocationString
              presentation.setTailText(tailText + " " + location, true)
            }
            case alias: ScTypeAliasDefinition => {
              presentation.setTypeText(presentationString(alias.aliasedType.getOrAny, substitutor))
            }
            case method: PsiMethod => {
              val str: String = presentationString(method.getReturnType, substitutor)
              if (resolveResult.isNamedParameter) {
                presentation.setTailText(" = " + str)
              } else {
                presentation.setTypeText(str)
                val params =
                  if (!isOverloadedForClassName) presentationString(method.getParameterList, substitutor)
                  else "(...)"
                val tailText1 = tailText + params + (
                  if (shouldImport && isClassName && containingClass != null)
                    " " + containingClass.getPresentation.getLocationString
                  else if (isClassName && containingClass != null)
                    " in " + containingClass.name + " " + containingClass.getPresentation.getLocationString
                  else ""
                  )
                presentation.setTailText(tailText1)
              }
            }
            case f: PsiField => {
              presentation.setTypeText(presentationString(f.getType, substitutor))
            }
            case _ =>
          }
          if (presentation.isReal)
            presentation.setIcon(element.getIcon(0))
          var itemText: String =
            if (isRenamed == None) if (isClassName && shouldImport) {
              val containingClass = ScalaPsiUtil.nameContext(element) match {
                case memb: PsiMember => memb.getContainingClass
                case _ => null
              }
              if (containingClass != null) containingClass.name + "." + name
              else name
            } else name
            else name + "<=" + element.name
          val someKey = Option(ignore.getUserData(someSmartCompletionKey)).map(_.booleanValue()).getOrElse(false)
          if (someKey) itemText = "Some(" + itemText + ")"
          presentation.setItemText(itemText)
          presentation.setStrikeout(isDeprecated)
          presentation.setItemTextBold(isBold)
          if (ScalaPsiUtil.getSettings(element.getProject).SHOW_IMPLICIT_CONVERSIONS)
            presentation.setItemTextUnderlined(isUnderlined)
        }
      })
      val returnLookupElement: LookupElement = lookupBuilder
      returnLookupElement.putUserData(isInImportKey, new java.lang.Boolean(isInImport))
      returnLookupElement.putUserData(isNamedParameterOrAssignment,
        new java.lang.Boolean(resolveResult.isNamedParameter || isAssignment))
      returnLookupElement.putUserData(isBoldKey, new java.lang.Boolean(isBold))
      returnLookupElement.putUserData(isUnderlinedKey, new java.lang.Boolean(isUnderlined))
      returnLookupElement.putUserData(shouldImportKey, new java.lang.Boolean(shouldImport))
      returnLookupElement.putUserData(classNameKey, new java.lang.Boolean(isClassName))
      returnLookupElement.putUserData(isInStableCodeReferenceKey, new java.lang.Boolean(isInStableCodeReference))

      (returnLookupElement, element, substitutor)
    }

    val name: String = isRenamed.getOrElse(element.name)
    val Setter = """(.*)_=""".r
    name match {
      case Setter(prefix) =>
        Seq(getLookupElementInternal(true, prefix), getLookupElementInternal(false, name))
      case _ => Seq(getLookupElementInternal(false, name))
    }
  }*/
}
