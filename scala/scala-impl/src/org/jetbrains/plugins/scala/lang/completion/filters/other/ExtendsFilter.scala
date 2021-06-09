package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class ExtendsFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))

    // class Test exten
    //           ^ find error here
    val errorBeforeExtendsStart = leaf.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

    errorBeforeExtendsStart match {
      case Some((_: PsiErrorElement) && PrevSibling(typeDefBeforeError: ScTypeDefinition)) =>
        if (typeDefBeforeError.extendsBlock.templateParents.isDefined) false
        else {
          // do not suggest if there is already an extends. i.e.:
          // class Test e<caret> extends
          !leaf.nextVisibleLeaf.exists(_.elementType == ScalaTokenTypes.kEXTENDS)
        }
      case _ =>
        false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'extends' keyword filter"
}