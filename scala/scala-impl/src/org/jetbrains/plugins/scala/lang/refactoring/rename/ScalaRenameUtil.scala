package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{ObjectExt, Parent, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util
import scala.jdk.CollectionConverters._

object ScalaRenameUtil {

  /**
   * Suppose we have import alias:
   * {{{
   *   object Wrapper {
   *      object Context {
   *         class MyDefinition<Caret>
   *      }
   *      import Context.{MyDefinition => MyDefinitionAliased}
   *      new MyDefinitionAliased
   *   }
   * }}}
   * If we rename `MyDefinition` we shouldn't rename `MyDefinitionAliased`.
   * Without this filtering it will be renamed because currently references to import aliases are transparent:
   * they resolve directly to the original definition name.
   */
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

  def addEndMarkerReference(@Nullable element: PsiNamedElement, references: util.Collection[PsiReference]): Unit = {
    val end = element match {
      case begin: ScBegin => begin.end
      case Parent(Parent(begin: ScValueOrVariable with ScBegin)) =>
        // ScVariableDefinition and ScValueDefinition are not ScBegin, so we need to go up two parents
        begin.end
      case _ => None
    }
    end
      .filter(_.name == element.name)
      .flatMap(_.asOptionOfUnsafe[PsiReference])
      .foreach(references.add)
  }

  def replaceImportClassReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    val result = allReferences.asScala.map {
      case ref: ScStableCodeReference =>
        val isInImport = ref.parentOfType(classOf[ScImportStmt]).isDefined
        if (isInImport && ref.resolve() == null) {
          val multiResolve = ref.multiResolveScala(false)
          if (multiResolve.length > 1 && multiResolve.forall(_.getElement.is[ScTypeDefinition])) {
            new PsiReference {
              override def getVariants: Array[AnyRef] = ref.getVariants

              override def getCanonicalText: String = ref.getCanonicalText

              override def getElement: PsiElement = ref.getElement

              override def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              override def bindToElement(element: PsiElement): PsiElement = ref.bindToElement(element)

              override def handleElementRename(newElementName: String): PsiElement = ref.handleElementRename(newElementName)

              override def isSoft: Boolean = ref.isSoft

              override def getRangeInElement: TextRange = ref.getRangeInElement

              override def resolve(): PsiElement = multiResolve.apply(0).getElement
            }
          } else ref
        } else ref
      case ref: PsiReference => ref
    }
    result.asJavaCollection
  }

  def findSubstituteElement(elementToRename: PsiElement): Option[PsiNamedElement] = {
    elementToRename match {
      case ScalaConstructor(constr) => Some(constr.containingClass)
      case fun: ScFunction if Seq("apply", "unapply", "unapplySeq") contains fun.name =>
        fun.containingClass match {
          case newTempl: ScNewTemplateDefinition => ScalaPsiUtil.findInstanceBinding(newTempl)
          case obj: ScObject if obj.isSyntheticObject => Some(obj.fakeCompanionClassOrCompanionClass)
          case clazz => Some(clazz)
        }
      case named: PsiNamedElement => Some(named)
      case _ => None
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
    val newElemRange = ScalaRenameUtil.findSubstituteElement(element).map(_.getTextRange)
    newElemRange.exists(nr => nr.getStartOffset == range.getStartOffset && nr.getEndOffset == range.getEndOffset)
  }
}
