package scala.meta

import scala.collection.Seq

import com.intellij.openapi.project.DumbService
import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

package object intellij {
  object psiExt {

    private def metaEnabled(element: ScalaPsiElement): Boolean = {
      !ScStubElementType.isStubBuilding &&
        !DumbService.isDumb(element.getProject) &&
        !element.isInCompiledFile &&
        element.containingFile.exists(IdeaUtil.inModuleWithParadisePlugin)
    }

    implicit class AnnotExt(val annotation: ScAnnotation) extends AnyVal {

      private def hasMetaAnnotation(results: Array[ScalaResolveResult]) = results.map(_.getElement).exists {
        case c: ScPrimaryConstructor => c.containingClass.isMetaAnnotatationImpl
        case o: ScTypeDefinition => o.isMetaAnnotatationImpl
        case _ => false
      }

      def isMetaMacro: Boolean = {
        if (!metaEnabled(annotation))
          return false

        @CachedInsidePsiElement(annotation, ModCount.getBlockModificationCount)
        def cached: Boolean = {
          annotation.constructor.reference.exists {
            case stRef: ScStableCodeReferenceElementImpl =>
              val processor = new ResolveProcessor(stRef.getKinds(incomplete = false), stRef, stRef.refName)
              hasMetaAnnotation(stRef.doResolve(processor))
            case _ => false
          }
        }

        cached
      }
    }

    implicit class AnnotHolderExt(val ah: ScAnnotationsHolder) extends AnyVal {
      def getMetaExpansion: Either[String, scala.meta.Tree] = {

        @CachedWithRecursionGuard(ah, Left("Recursive meta expansion"), ModCount.getBlockModificationCount)
        def getMetaExpansionCached: Either[String, Tree] = {
          import ScalaProjectSettings.ScalaMetaMode
          if (ScalaProjectSettings.getInstance(ah.getProject).getScalaMetaMode == ScalaMetaMode.Enabled) {
            val metaAnnotation = ah.annotations.find(AnnotExt(_).isMetaMacro)
            metaAnnotation match {
              case Some(annot) => MetaExpansionsManager.runMetaAnnotation(annot)
              case None => Left("No meta annotation")
            }
          } else Left("Meta expansions disabled in settings")
        }

        if (!metaEnabled(ah))
          Left("")
        else
          getMetaExpansionCached
      }

      def getMetaCompanionObject: Option[scala.meta.Defn.Object] = {
        import scala.{meta => m}

        if (!metaEnabled(ah)) return None

        ah.getMetaExpansion match {
          case Left(_) => None
          case Right(m.Term.Block(Seq(_: m.Defn, obj: m.Defn.Object))) => Some(obj)
          case Right(_) => None
        }
      }
    }

    implicit class TemplateDefExt(val td: ScTemplateDefinition) extends AnyVal {
      def isMetaAnnotatationImpl: Boolean = {
        td.members.exists(_.getModifierList.findChildrenByType(ScalaTokenTypes.kINLINE).nonEmpty) ||
          td.members.exists({case ah: ScAnnotationsHolder => ah.hasAnnotation("scala.meta.internal.inline.inline")})
      }
    }

  }
}
