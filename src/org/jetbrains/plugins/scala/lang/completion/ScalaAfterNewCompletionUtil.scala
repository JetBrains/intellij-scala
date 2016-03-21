package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionResultSet, InsertHandler}
import com.intellij.codeInsight.lookup.{AutoCompletionPolicy, LookupElement, LookupElementPresentation, LookupElementRenderer}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiNamedElement}
import com.intellij.util.{ProcessingContext, Processor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaConstructorInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

import scala.collection.mutable

/**
 * @author Alefas
 * @since 27.03.12
 */
object ScalaAfterNewCompletionUtil {
  lazy val afterNewPattern = ScalaSmartCompletionContributor.superParentsPattern(classOf[ScStableCodeReferenceElement],
    classOf[ScSimpleTypeElement], classOf[ScConstructor], classOf[ScClassParents], classOf[ScExtendsBlock], classOf[ScNewTemplateDefinition])

  def expectedTypesAfterNew(position: PsiElement, context: ProcessingContext): (Array[ScType], Boolean) = {
    val isAfter = isAfterNew(position, context)
    val data = if (isAfter) {
      val element = position
      val newExpr: ScNewTemplateDefinition = PsiTreeUtil.getContextOfType(element, classOf[ScNewTemplateDefinition])
      newExpr.expectedTypes().map {
        case ScAbstractType(_, lower, upper) => upper
        case tp => tp
      }
    } else Array[ScType]()

    (data, isAfter)
  }

  def isAfterNew(position: PsiElement, context: ProcessingContext): Boolean =
    afterNewPattern.accepts(position, context)

  def getLookupElementFromClass(expectedTypes: Array[ScType], clazz: PsiClass,
                                renamesMap: mutable.HashMap[String, (String, PsiNamedElement)])
                               (implicit typeSystem: TypeSystem): LookupElement = {
    val undefines: Seq[ScUndefinedType] = clazz.getTypeParameters.map(ptp =>
      new ScUndefinedType(new ScTypeParameterType(ptp, ScSubstitutor.empty))
    )
    val predefinedType =
      if (clazz.getTypeParameters.length == 1) {
        ScParameterizedType(ScDesignatorType(clazz), undefines)
      }
      else
        ScDesignatorType(clazz)
    val noUndefType =
      if (clazz.getTypeParameters.length == 1) {
        ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(ptp =>
          new ScTypeParameterType(ptp, ScSubstitutor.empty)
        ))
      }
      else
        ScDesignatorType(clazz)

    val iterator = expectedTypes.iterator
    while (iterator.hasNext) {
      val typez = iterator.next()
      val conformance = predefinedType.conforms(typez, new ScUndefinedSubstitutor())
      if (conformance._1) {
        conformance._2.getSubstitutor match {
          case Some(subst) =>
            val lookupElement = getLookupElementFromTypeAndClass(subst.subst(noUndefType), clazz,
              ScSubstitutor.empty, new AfterNewLookupElementRenderer(_, _, _), new ScalaConstructorInsertHandler, renamesMap)
            for (undefine <- undefines) {
              subst.subst(undefine) match {
                case ScUndefinedType(_) =>
                  lookupElement.typeParametersProblem = true
                case _ =>
              }
            }
            return lookupElement
          case _ =>
        }
      }
    }
    val lookupElement = getLookupElementFromTypeAndClass(noUndefType, clazz, ScSubstitutor.empty,
      new AfterNewLookupElementRenderer(_, _, _), new ScalaConstructorInsertHandler, renamesMap)
    if (undefines.nonEmpty) {
      lookupElement.typeParametersProblem = true
    }
    lookupElement
  }

  class AfterNewLookupElementRenderer(tp: ScType, psiClass: PsiClass,
                                      subst: ScSubstitutor) extends LookupElementRenderer[LookupElement] {
    def renderElement(ignore: LookupElement, presentation: LookupElementPresentation) {
      var isDeprecated = false
      psiClass match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => isDeprecated = true
        case _ =>
      }
      var tailText: String = ""
      val itemText: String = psiClass.name + (tp match {
        case ScParameterizedType(_, tps) =>
          tps.map(subst.subst(_).presentableText).mkString("[", ", ", "]")
        case _ => ""
      })
      psiClass match {
        case clazz: PsiClass =>
          if (psiClass.isInterface || psiClass.isInstanceOf[ScTrait] ||
            psiClass.hasModifierPropertyScala("abstract")) {
            tailText += " {...}"
          }
          val location: String = clazz.getPresentation.getLocationString
          presentation.setTailText(tailText + " " + location, true)
        case _ =>
      }
      presentation.setIcon(psiClass.getIcon(0))
      presentation.setStrikeout(isDeprecated)
      presentation.setItemText(itemText)
    }
  }


  private def getLookupElementFromTypeAndClass(tp: ScType, psiClass: PsiClass, subst: ScSubstitutor,
                                       renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                                       insertHandler: InsertHandler[LookupElement],
                                       renamesMap: mutable.HashMap[String, (String, PsiNamedElement)]): ScalaLookupItem = {
    val name: String = psiClass.name
    val isRenamed = renamesMap.filter {
      case (aName, (renamed, aClazz)) => aName == name && aClazz == psiClass
    }.map(_._2._1).headOption
    val lookupElement: ScalaLookupItem = new ScalaLookupItem(psiClass, isRenamed.getOrElse(name)) {
      override def renderElement(presentation: LookupElementPresentation) {
        renderer(tp, psiClass, subst).renderElement(this, presentation)
        isRenamed match {
          case Some(nme) => presentation.setItemText(nme + " <= " + presentation.getItemText)
          case _ =>
        }
      }
    }
    lookupElement.isRenamed = isRenamed
    if (ApplicationManager.getApplication.isUnitTestMode || psiClass.isInterface ||
      psiClass.isInstanceOf[ScTrait] || psiClass.hasModifierPropertyScala("abstract"))
      lookupElement.setAutoCompletionPolicy(if (ApplicationManager.getApplication.isUnitTestMode) AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
      else AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
    val qualName = psiClass.qualifiedName
    if (ScalaCodeStyleSettings.getInstance(psiClass.getProject).hasImportWithPrefix(qualName)) {
      lookupElement.prefixCompletion = true
    }
    lookupElement.setInsertHandler(new ScalaConstructorInsertHandler)
    tp match {
      case ScParameterizedType(_, tps) => lookupElement.typeParameters = tps
      case _ =>
    }
    lookupElement
  }

  def convertTypeToLookupElement(tp: ScType, place: PsiElement, addedClasses: mutable.HashSet[String],
                                 renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                                 insertHandler: InsertHandler[LookupElement],
                                 renamesMap: mutable.HashMap[String, (String, PsiNamedElement)])
                                (implicit typeSystem: TypeSystem): ScalaLookupItem = {
    tp.extractClassType(place.getProject) match {
      case Some((clazz: PsiClass, subst: ScSubstitutor)) =>
        //filter base types (it's important for scala 2.9)
        clazz.qualifiedName match {
          case "scala.Boolean" | "scala.Int" | "scala.Long" | "scala.Byte" | "scala.Short" | "scala.AnyVal" |
               "scala.Char" | "scala.Unit" | "scala.Float" | "scala.Double" | "scala.Any" => return null
          case _ =>
        }
        //todo: filter inner classes smarter (how? don't forget deep inner classes)
        if (clazz.containingClass != null && (!clazz.containingClass.isInstanceOf[ScObject] ||
          clazz.hasModifierPropertyScala("static"))) return null
        if (!ResolveUtils.isAccessible(clazz, place, forCompletion = true)) return null
        if (addedClasses.contains(clazz.qualifiedName)) return null
        addedClasses += clazz.qualifiedName
        getLookupElementFromTypeAndClass(tp, clazz, subst, renderer, insertHandler, renamesMap)
      case _ => null
    }
  }

  def collectInheritorsForType(typez: ScType, place: PsiElement, addedClasses: mutable.HashSet[String],
                               result: CompletionResultSet,
                               renderer: (ScType, PsiClass, ScSubstitutor) => LookupElementRenderer[LookupElement],
                               insertHandler: InsertHandler[LookupElement],
                               renamesMap: mutable.HashMap[String, (String, PsiNamedElement)])
                              (implicit typeSystem: TypeSystem) {
    typez.extractClassType(place.getProject) match {
      case Some((clazz, subst)) =>
        //this change is important for Scala Worksheet/Script classes. Will not find inheritors, due to file copy.
        val searchScope =
          if (clazz.getUseScope.isInstanceOf[LocalSearchScope]) GlobalSearchScope.allScope(place.getProject)
          else clazz.getUseScope
        ClassInheritorsSearch.search(clazz, searchScope, true).forEach(new Processor[PsiClass] {
          def process(clazz: PsiClass): Boolean = {
            if (clazz.name == null || clazz.name == "") return true
            val undefines: Seq[ScUndefinedType] = clazz.getTypeParameters.map(ptp =>
              new ScUndefinedType(new ScTypeParameterType(ptp, ScSubstitutor.empty))
            )
            val predefinedType =
              if (clazz.getTypeParameters.nonEmpty) {
                ScParameterizedType(ScDesignatorType(clazz), undefines)
              }
              else ScDesignatorType(clazz)
            val noUndefType =
              if (clazz.getTypeParameters.nonEmpty) {
                ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(ptp =>
                  new ScTypeParameterType(ptp, ScSubstitutor.empty)
                ))
              }
              else ScDesignatorType(clazz)
            val conformance = predefinedType.conforms(typez, new ScUndefinedSubstitutor())
            if (!conformance._1) return true
            conformance._2.getSubstitutor match {
              case Some(undefSubst) =>
                val lookupElement = convertTypeToLookupElement(undefSubst.subst(noUndefType), place, addedClasses,
                  renderer, insertHandler, renamesMap)
                if (lookupElement != null) {
                  for (undefine <- undefines) {
                    undefSubst.subst(undefine) match {
                      case ScUndefinedType(_) =>
                        lookupElement.typeParametersProblem = true
                      case _ =>
                    }
                  }
                  result.addElement(lookupElement)
                }
              case _ =>
            }
            true
          }
        })
      case _ =>
    }
  }
}
