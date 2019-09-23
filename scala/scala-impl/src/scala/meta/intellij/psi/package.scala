package scala.meta
package intellij

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

package object psi {

  implicit class AnnotationExt(private val repr: ScAnnotation) extends AnyVal {

    def isMetaMacro: Boolean = repr.isMetaEnabled && cached

    @CachedInUserData(repr, ModCount.getBlockModificationCount)
    private def cached: Boolean = repr.constructorInvocation.reference.exists {
      case reference: ScStableCodeReferenceImpl =>
        val processor = new ResolveProcessor(reference.getKinds(incomplete = false), reference, reference.refName)
        reference.doResolve(processor).collect {
          case ScalaResolveResult(definition: ScTypeDefinition, _) => definition
          case ScalaResolveResult(constructor: ScPrimaryConstructor, _) => constructor.containingClass
        }.exists(hasInlineAnnotation.unapply)
      case _ => false
    }
  }

  implicit class AnnotationHolderExt(private val repr: ScAnnotationsHolder) extends AnyVal {

    def metaExpand: Either[String, Tree] =
      if (repr.isMetaEnabled) cached
      else Left("")

    @CachedWithRecursionGuard(repr, Left("Recursive meta expansion"), ModCount.getBlockModificationCount)
    private def cached: Either[String, Tree] = {
      import ScalaProjectSettings.ScalaMetaMode.Enabled
      ScalaProjectSettings.getInstance(repr.getProject).getScalaMetaMode match {
        case Enabled =>
          repr.annotations.find(_.isMetaMacro)
            .toRight("No meta annotation")
            .flatMap(MetaExpansionsManager.runMetaAnnotation)
        case _ => Left("Meta expansions disabled in settings")
      }
    }
  }

  object hasInlineAnnotation {

    def unapply(definition: ScTemplateDefinition): Boolean =
      definition.membersWithSynthetic.exists {
        case member if hasInlineModifier(member) => true
        case holder: ScAnnotationsHolder => holder.hasAnnotation("scala.meta.internal.inline.inline")
        case _ => false
      }

    private def hasInlineModifier(member: ScMember) =
      member.getModifierList.isInline
  }

}
