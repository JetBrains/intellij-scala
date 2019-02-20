package org.jetbrains.plugins.scala
package lang.refactoring.rename

import java.util

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.collection.JavaConverters._

object ScalaRenameUtil {
  def filterAliasedReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    val filtered = allReferences.asScala.filterNot(isAliased)
    filtered.asJavaCollection
  }

  def isAliased(ref: PsiReference): Boolean = ref match {
    case resolvableReferenceElement: ScReference =>
      resolvableReferenceElement.bind() match {
        case Some(result) =>
          val renamed = result.isRenamed
          renamed.nonEmpty
        case None => false
      }
    case _ => false
  }

  def isIndirectReference(ref: PsiReference, element: PsiElement): Boolean = ref match {
    case scRef: ScReference => scRef.isIndirectReferenceTo(ref.resolve(), element)
    case _ => false
  }

  def findReferences(element: PsiElement): util.ArrayList[PsiReference] = {
    val allRefs = ReferencesSearch.search(element, element.getUseScope).findAll()
    val filtered = allRefs.asScala.filterNot(isAliased).filterNot(isIndirectReference(_, element))
    new util.ArrayList[PsiReference](filtered.asJavaCollection)
  }

  def replaceImportClassReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    val result = allReferences.asScala.map {
      case ref: ScStableCodeReference =>
        val isInImport = ref.parentOfType(classOf[ScImportStmt]).isDefined
        if (isInImport && ref.resolve() == null) {
          val multiResolve = ref.multiResolveScala(false)
          if (multiResolve.length > 1 && multiResolve.forall(_.getElement.isInstanceOf[ScTypeDefinition])) {
            new PsiReference {
              override def getVariants: Array[AnyRef] = ref.getVariants

              def getCanonicalText: String = ref.getCanonicalText

              def getElement: PsiElement = ref.getElement

              def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              def bindToElement(element: PsiElement): PsiElement = ref.bindToElement(element)

              def handleElementRename(newElementName: String): PsiElement = ref.handleElementRename(newElementName)

              def isSoft: Boolean = ref.isSoft

              def getRangeInElement: TextRange = ref.getRangeInElement

              def resolve(): PsiElement = multiResolve.apply(0).getElement
            }
          } else ref
        } else ref
      case ref: PsiReference => ref
    }
    result.asJavaCollection
  }

  def findSubstituteElement(elementToRename: PsiElement): PsiNamedElement = {
    elementToRename match {
      case ScalaConstructor(constr) => constr.containingClass
      case fun: ScFunction if Seq("apply", "unapply", "unapplySeq") contains fun.name =>
        fun.containingClass match {
          case newTempl: ScNewTemplateDefinition => ScalaPsiUtil.findInstanceBinding(newTempl).orNull
          case obj: ScObject if obj.isSyntheticObject => obj.fakeCompanionClassOrCompanionClass
          case clazz => clazz
        }
      case named: PsiNamedElement => named
      case _ => null
    }
  }

  def doRenameGenericNamedElement(namedElement: PsiElement,
                                  newName: String,
                                  usages: Array[UsageInfo],
                                  listener: RefactoringElementListener): Unit = {
    case class UsagesWithName(name: String, usages: Array[UsageInfo])

    def encodeNames(usagesWithName: UsagesWithName): Seq[UsagesWithName] = {
      val UsagesWithName(name, usagez) = usagesWithName

      if (usagez.isEmpty) Nil
      else {
        val encodedName = ScalaNamesUtil.toJavaName(newName)
        if (encodedName == name) Seq(UsagesWithName(name, usagez))
        else {
          val needEncodedName: UsageInfo => Boolean = { u =>
            val ref = u.getReference.getElement
            !ref.getLanguage.isKindOf(ScalaLanguage.INSTANCE) //todo more concise condition?
          }
          val (usagesEncoded, usagesPlain) = usagez.partition(needEncodedName)
          Seq(UsagesWithName(encodedName, usagesEncoded), UsagesWithName(name, usagesPlain))
        }
      }
    }

    def modifyScObjectName(usagesWithName: UsagesWithName): Seq[UsagesWithName] = {
      val UsagesWithName(name, usagez) = usagesWithName
      if (usagez.isEmpty) Nil
      else {
        def needDollarSign(u: UsageInfo): Boolean = {
          u.getReference match {
            case null => false
            case _: ScReference => false
            case ref if ref.getElement.isInstanceOf[ScalaPsiElement] => false
            case _ => true
          }
        }
        val (usagesWithDS, usagesPlain) = usagez.partition(needDollarSign)
        Seq(UsagesWithName(name + "$", usagesWithDS), UsagesWithName(name, usagesPlain))
      }
    }

    def modifySetterName(usagesWithName: UsagesWithName): Seq[UsagesWithName] = {
      val UsagesWithName(name, usagez) = usagesWithName
      if (usagez.isEmpty) Nil
      else {
        val newNameWithoutSuffix = name.stripSuffix(setterSuffix(name))
        val grouped = usagez.groupBy(u => setterSuffix(u.getElement.getText))
        grouped.map(entry => UsagesWithName(newNameWithoutSuffix + entry._1, entry._2)).toSeq
      }
    }

    val encoded = encodeNames(UsagesWithName(newName, usages))
    val modified = namedElement match {
      case _: ScObject => encoded.flatMap(modifyScObjectName)
      case _: PsiTypedDefinitionWrapper | _: FakePsiMethod => encoded.flatMap(modifySetterName)
      case fun: ScFunction if setterSuffix(fun.name) != "" => encoded.flatMap(modifySetterName)
      case _: ScReferencePattern => encoded.flatMap(modifySetterName)
      case _ => encoded
    }
    modified.foreach {
      case UsagesWithName(name, usagez) if usagez.nonEmpty =>
        RenameUtil.doRenameGenericNamedElement(namedElement, name, usagez, listener)
      case _ =>
    }
      //to guarantee correct name of namedElement itself
    RenameUtil.doRenameGenericNamedElement(namedElement, newName, Array.empty[UsageInfo], listener)
  }

  def setterSuffix(name: String): String = {
    if (name.endsWith("_=")) "_="
    else if (name.endsWith("_$eq")) "_$eq"
    else ""
  }

  def sameElement(range: RangeMarker, element: PsiElement): Boolean = {
    val newElemRange = Option(ScalaRenameUtil.findSubstituteElement(element)).map(_.getTextRange)
    newElemRange.exists(nr => nr.getStartOffset == range.getStartOffset && nr.getEndOffset == range.getEndOffset)
  }
}
