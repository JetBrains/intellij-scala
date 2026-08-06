package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

object ScMemberAnnotator extends ElementAnnotator[ScMember] {

  override def annotate(element: ScMember, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    annotateWrongTopLevelMembers(element)
  }

  private def annotateWrongTopLevelMembers(element: ScMember)
                                          (implicit holder: ScalaAnnotationHolder): Unit = {
    if (
      element.isTopLevel &&
        cannotBeTopLevelMemberInScala2(element) &&
        element.scalaLanguageLevel.exists(_.isScala2) &&
        // The problem is that we have many situations when it's not clear whether top-level definitions are allowed,
        // because we are not in a real scala file, but some unittest or scripting editor.
        // We know, however, that when a definition is in a package then it's not allowed.
        // This fix might not be 100% correct, but I think the false positives are much worse than the false negatives.
        // So it's better not to annotate a top-level val that is not allowed in scala 2 than annotating one that is allowed.
        element.parents.exists(_.is[ScPackageLike]) &&
        // Mill allows top level definitions in its build.mill scripts
        !element.containingScalaFile.exists(_.isMillFile)
    ) {
      val elementToAnnotate = element.depthFirst().find(c => keywords(c.elementType)).getOrElse(element)
      holder.createErrorAnnotation(elementToAnnotate, ScalaBundle.message("cannot.be.a.top.level.definition.in.scala.2"))
    }
  }

  private def cannotBeTopLevelMemberInScala2(element: ScMember): Boolean =
    element.is[ScValueOrVariable, ScFunction, ScTypeAlias]

  private val keywords = Set(ScalaTokenTypes.kVAL, ScalaTokenTypes.kVAR, ScalaTokenTypes.kDEF, ScalaTokenTypes.kTYPE)
}
