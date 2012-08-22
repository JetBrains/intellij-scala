package org.jetbrains.plugins.scala.lang.refactoring.rename

import com.intellij.refactoring.rename.RenameJavaClassProcessor
import java.util.Map
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi.{PsiReference, PsiElement}
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import collection.JavaConverters.{asJavaCollectionConverter, iterableAsScalaIterableConverter}
import scala.Some
import annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class RenameScalaClassProcessor extends RenameJavaClassProcessor {
  override def canProcessElement(element: PsiElement): Boolean = {
    element.isInstanceOf[ScTypeDefinition] || element.isInstanceOf[PsiClassWrapper] || element.isInstanceOf[ScTypeParam]
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case wrapper: PsiClassWrapper => wrapper.definition
      case _ => element
    }
  }

  override def findReferences(element: PsiElement) = ScalaRenameUtil.filterAliasedReferences(super.findReferences(element))

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]) {
    element match {
      case td: ScTypeDefinition => {
        ScalaPsiUtil.getCompanionModule(td) match {
          case Some(td) => allRenames.put(td, newName)
          case _ =>
        }
        @tailrec
        def isTop(element: PsiElement): Boolean = {
          element match {
            case null => true
            case td: ScTemplateDefinition => false
            case _ => isTop(element.getContext)
          }
        }
        val file = td.getContainingFile
        if (file != null && isTop(element.getContext) && file.name == td.name + ".scala") {
          allRenames.put(file, newName + ".scala")
        }
      }
      case docTagParam: ScTypeParam =>
        docTagParam.owner match {
          case commentOwner: ScDocCommentOwner =>
            commentOwner.getDocComment match {
              case comment: ScDocComment =>
                comment.findTagsByName(MyScaladocParsing.TYPE_PARAM_TAG).foreach {
                  b => if (b.getValueElement != null && b.getValueElement.getText == docTagParam.name)
                    allRenames.put(b.getValueElement, newName)
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }

    //put rename for fake object companion class
    element match {
      case o: ScObject =>
        o.fakeCompanionClass match {
          case Some(clazz) => allRenames.put(clazz, newName + "$")
          case None =>
        }
      case t: ScTrait =>
        allRenames.put(t.fakeCompanionClass, newName + "$class")
      case _ =>
    }
  }
}