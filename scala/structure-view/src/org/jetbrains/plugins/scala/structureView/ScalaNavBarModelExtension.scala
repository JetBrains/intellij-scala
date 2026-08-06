package org.jetbrains.plugins.scala.structureView

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAlias, ScTypeDefinition, ScTypeDefinitionLike}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.projectView.FileKind
import org.jetbrains.plugins.scala.structureView.element.Element

final class ScalaNavBarModelExtension extends StructureAwareNavBarModelExtension {

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  // include Scala 3, Worksheet, etc.
  override def isAcceptableLanguage(element: PsiElement): Boolean =
    element != null && element.getLanguage.isKindOf(getLanguage)

  @Nullable
  override def adjustElement(psiElement: PsiElement): PsiElement = psiElement match {
    // When a Scala file is only the type named after the file, optionally with its companion,
    // showing both `MyClass.scala` and `MyClass` would duplicate the same navigation level.
    // Replace the file element itself with the representative type, so the navbar starts
    // from `MyClass` instead of the physical file node.
    case file: ScalaFile => singleTopLevelFileRepresentative(file).getOrElse(file)
    case _ => psiElement
  }

  override protected def acceptParentFromModel(psiElement: PsiElement): Boolean = psiElement match {
    // After `adjustElement` substitutes the file with its representative type,
    // accepting the original file as a parent would reintroduce the duplicate `MyClass.scala > MyClass` path.
    // Report that the file parent is not acceptable exactly when such a representative
    // exists, so the model skips the file node and keeps only the type node.
    case file: ScalaFile => singleTopLevelFileRepresentative(file).isEmpty
    case _ => true
  }

  private def singleTopLevelFileRepresentative(file: ScalaFile): Option[ScTypeDefinitionLike] =
    // `FileKind` owns the file-shape check: it returns a representative only for files whose
    // contents are the matching top-level type definition and, at most, its companion.
    FileKind.getForFile(file).map(_.representative)

  @Nullable
  override def getPresentableText(item: Any): String = getPresentableText(item, false)

  @Nullable
  override def getPresentableText(item: Any, forPopup: Boolean): String = item match {
    case file: ScalaFile =>
      file.name
    case element: ScalaPsiElement =>
      val resultText = element match {
        case v: ScValueOrVariable => getPresentableText_ForVarOrVal(v)
        case _: ScExtension       => Some("extension")
        case td: ScTypeDefinition => getPresentableText_ForTypeDefinition(td)
        case ga: ScGivenAlias     => getPresentableText_ForGivenAlias(ga)
        case ntd: ScNewTemplateDefinition if ntd.isAnonymous => getPresentableText_ForAnonymousClass(ntd)
        case n: ScNamedElement      => Option(n.name)
        case _                      => Element.forPsiElement(element).map(_.getPresentableText)
      }
      resultText.orNull
    case _ =>
      null
  }

  private def getPresentableText_ForVarOrVal(valOrVar: ScValueOrVariable): Option[String] =
    valOrVar match {
      case definition: ScValueOrVariableDefinition if !definition.isSimple =>
        //Example: `val (x, y) = ...` show full `(x, y)`
        Some(definition.pList.getText)
      case _ =>
        val namedElement = valOrVar.declaredElements.headOption
        namedElement.flatMap(el => Option(el.name))
    }

  private def getPresentableText_ForTypeDefinition(typeDefinition: ScTypeDefinition): Option[String] =
    Option(typeDefinition.name)

  private def getPresentableText_ForGivenAlias(givenAlias: ScGivenAlias): Option[String] =
    Option(givenAlias.name)

  private def getPresentableText_ForAnonymousClass(definition: ScNewTemplateDefinition): Option[String] = {
    val baseTypeName = definition.firstConstructorInvocation.flatMap(_.simpleTypeElement).map(_.getText)
    baseTypeName.map(typeName => s"anonymous $typeName")
  }

  @Nullable
  override def getParent(psiElement: PsiElement): PsiElement = psiElement match {
    case v: ScValueOrVariable =>
      // We have a special handling for val/var definitions. We can't rely on the default structure-view-based solution
      // (BTW, there is no that much logic in the structure-view-based solution)
      // The reason is that we can't fully mirror the structure view.
      // Let's say we have a val definition with pattern that defines no bindings:
      // `val Some(_) = { ... }`
      // In this case structure view won't contain any elements for it as it doesn't have a name definition
      // But in Nav Bar we want to still show it as is `Some(_)` (we couldn't see which better alternative to display in this case
      getParent_FromPsiParents(v).orNull
    case definition: ScNewTemplateDefinition =>
      getParent_FromPsiParents(definition).orNull
    case _ =>
      super.getParent(psiElement)
  }

  private def getParent_FromPsiParents(element: PsiElement): Option[PsiElement] = {
    element.parents.collectFirst {
      case p: ScValueOrVariable => p
      case p: ScFunction => p
      case p: ScNewTemplateDefinition => p
      case p: ScTypeDefinition => p
      case p: ScalaFile => p
    }
  }
}
