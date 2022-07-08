package org.jetbrains.plugins.scala.lang
package completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

final class ScalaMemberNameCompletionContributor extends ScalaCompletionContributor {
  //suggest class name
  extend(
    CompletionType.BASIC,
    identifierWithParentPattern(classOf[ScTypeDefinition]),
    new ScalaCompletionProvider {

      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Iterable[LookupElementBuilder] = {
        val typeDefinition = position.getContext
        typeDefinition.getContext match {
          case null => Iterable.empty
          case parent =>
            val (objects, classes) = objectsAndClassesIn(parent)
            val (targetNames, companionNames) = toNames(typeDefinition, classes, objects)

            (findFileName(parent) ++ targetNames)
              .filterNot(companionNames)
              .map(LookupElementBuilder.create)
        }
      }

      private def objectsAndClassesIn(parent: PsiElement) = {
        import scala.jdk.CollectionConverters._
        getChildrenOfTypeAsList(parent, classOf[ScTypeDefinition])
          .asScala
          .toSet
          .partition(_.isObject)
      }

      private def toNames(typeDefinition: PsiElement,
                          classes: Set[ScTypeDefinition],
                          objects: Set[ScTypeDefinition]): (Set[String], Set[String]) = {
        val classNames = classes.map(_.name)
        val objectNames = objects.map(_.name)

        typeDefinition match {
          case _: ScClass |
               _: ScTrait =>
            (objectNames, classNames)
          case _: ScObject =>
            (classNames, objectNames)
          case _ /*: ScEnum */ =>
            (Set.empty[String], classNames union objectNames)
        }
      }

      private def findFileName(parent: PsiElement)
                              (implicit parameters: CompletionParameters) =
        parent match {
          case _: ScalaFile |
               _: ScPackaging => Some {
            parameters
              .getOriginalFile
              .getVirtualFile
              .getNameWithoutExtension
          }
          case _ => None
        }
    }
  )
}
