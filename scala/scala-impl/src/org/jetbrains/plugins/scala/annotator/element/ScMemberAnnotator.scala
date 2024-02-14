package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

object ScMemberAnnotator extends ElementAnnotator[ScMember] {

  override def annotate(element: ScMember, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    annotateWrongTopLevelMembers(element)
  }

  // TODO package private
  private def annotateWrongTopLevelMembers(element: ScMember)
                                          (implicit holder: ScalaAnnotationHolder): Unit = {
    if (
      element.isTopLevel &&
        cannotBeTopLevelMemberInScala2(element) &&
        element.scalaLanguageLevel.exists(_.isScala2) &&
        element.containingScalaFile.exists(file => file.getFileType == ScalaFileType.INSTANCE && !file.isWorksheetFile) &&
        !ApplicationManager.getApplication.isUnitTestMode
    ) {
      val elementToAnnotate = element.depthFirst().find(c => keywords(c.elementType)).getOrElse(element)
      holder.createErrorAnnotation(elementToAnnotate, ScalaBundle.message("cannot.be.a.top.level.definition.in.scala.2"))
    }
  }

  private def cannotBeTopLevelMemberInScala2(element: ScMember): Boolean =
    element.is[ScValueOrVariable, ScFunction, ScTypeAlias]

  private val keywords = Set(ScalaTokenTypes.kVAL, ScalaTokenTypes.kVAR, ScalaTokenTypes.kDEF, ScalaTokenTypes.kTYPE)
}
