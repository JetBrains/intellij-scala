package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

final class ScalaMemberNameCompletionContributor extends ScalaCompletionContributor {
  //suggest class name
  extend(
    CompletionType.BASIC,
    identifierWithParentPattern(classOf[ScTypeDefinitionLike]),
    new ScalaCompletionProvider {

      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters,
                                            context: ProcessingContext): Iterable[LookupElementBuilder] = {
        val typeDefinition = position.getContext
        typeDefinition.getContext match {
          case null => Iterable.empty
          case parent =>
            val (objects, types) = objectsAndTypesIn(parent)
            val (targetNames, companionNames) = toNames(typeDefinition, types, objects)

            (findFileName(parent) ++ targetNames)
              .filterNot(companionNames)
              .map(LookupElementBuilder.create)
        }
      }

      private def objectsAndTypesIn(parent: PsiElement) = {
        import scala.jdk.CollectionConverters._
        getChildrenOfTypeAsList(parent, classOf[ScTypeDefinitionLike])
          .asScala
          .filter(_.canHaveCompanion)
          .toSet
          .partition(_.isObject)
      }

      private def toNames(typeDefinition: PsiElement,
                          types: Set[ScTypeDefinitionLike],
                          objects: Set[ScTypeDefinitionLike]): (Set[String], Set[String]) = {
        val typeNames = types.map(_.name)
        val objectNames = objects.map(_.name)

        typeDefinition match {
          case _: ScClass |
               _: ScTrait |
               _: ScTypeAlias =>
            (objectNames, typeNames)
          case _: ScObject =>
            (typeNames, objectNames)
          case _ /*: ScEnum */ =>
            (Set.empty[String], typeNames union objectNames)
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
