package scala.meta
package intellij

import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, CachedWithRecursionGuard}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.meta.intellij.MetaExpansionsManager.MetaAnnotationError

package object psi {

  implicit class AnnotationExt(private val repr: ScAnnotation) extends AnyVal {

    def isMetaMacro: Boolean = repr.isMetaEnabled && cached

    @CachedInUserData(repr, BlockModificationTracker(repr))
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

    def metaExpand: Either[MetaAnnotationError, Tree] =
      if (repr.isMetaEnabled && isMetaAnnotationExpansionEnabled) cached
      else Left(MetaAnnotationError(NlsString.force("")))

    @CachedWithRecursionGuard(repr, Left(MetaAnnotationError(ScalaMetaBundle.nls("recursive.meta.expansion"), None)), BlockModificationTracker(repr))
    private def cached: Either[MetaAnnotationError, Tree] = {
      import ScalaProjectSettings.ScalaMetaMode.Enabled
      ScalaProjectSettings.getInstance(repr.getProject).getScalaMetaMode match {
        case Enabled =>
          repr.annotations.find(_.isMetaMacro)
            .toRight(MetaAnnotationError(ScalaMetaBundle.nls("no.meta.annotation")))
            .flatMap(MetaExpansionsManager.runMetaAnnotation)
        case _ =>
          Left(MetaAnnotationError(ScalaMetaBundle.nls("meta.expansions.disabled.in.settings")))
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
