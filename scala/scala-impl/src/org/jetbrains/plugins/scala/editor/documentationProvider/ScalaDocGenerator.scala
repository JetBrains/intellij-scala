package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.editor.documentationProvider.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.extensions.{&&, PsiClassExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.HtmlPsiUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScTypeAlias, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.util.{Failure, Try}

object ScalaDocGenerator {

  private final case class ActualComment(owner: PsiDocCommentOwner, comment: PsiDocComment, isInherited: Boolean)

  // IDEA doesn't log exceptions occurred during doc rendering,
  // we would like to at least show them in internal mode, during development
  private def internalLog[T](body: => T): T = Try(body).fold(
    ex => {
      if (ApplicationManager.getApplication.isInternal)
        ex.printStackTrace()
      throw ex
    },
    identity
  )

  def generateDoc(elementWithDoc: PsiElement, originalElement: Option[PsiElement]): String = internalLog {
    val builder = new StringBuilder


    builder.append("<html>")
    builder.append("<head><style>").append(ScalaDocCss.value).append("</style></head>")
    builder.append("<body>")

    val e = elementWithDoc.getNavigationElement // TODO: check what is this?
    ScalaDocDefinitionGenerator.generate(builder, e, originalElement)
    generateDocContent(builder, e)

    builder.append("</body>")
    builder.append("</html>")

    val result = builder.result()
    result
  }

  def generateDocRendered(commentOwner: ScDocCommentOwner, comment: ScDocComment): String = internalLog {
    val builder = new StringBuilder
    new ScalaDocContentWithSectionsGenerator(commentOwner, comment, rendered = true).generate(builder)
    builder.result
  }

  private def generateDocContent(builder: StringBuilder, e: PsiElement): Unit =
    for {
      commentOwner  <- getCommentOwner(e)
      actualComment <- findActualComment(commentOwner)
    } yield generateDocComment(builder, actualComment)

  private def getCommentOwner(e: PsiElement): Option[PsiDocCommentOwner] =
    e match {
      case typeDef: ScTypeDefinition => Some(typeDef)
      case fun: ScFunction           => Some(fun)
      case tpe: ScTypeAlias          => Some(tpe)
      case decl: ScValueOrVariable   => Some(decl)
      case pattern: ScBindingPattern =>
        pattern.nameContext match {
          case (definition: ScValueOrVariable) && (_: ScPatternDefinition | _: ScVariableDefinition) =>
            Some(definition)
          case _ => None
        }
      case _ => None
    }

  private def generateDocComment(builder: StringBuilder, actualComment: ActualComment): Unit = {
    if (actualComment.isInherited)
      builder.append(inheritedDisclaimer(actualComment.owner.containingClass))

    actualComment match {
      case ActualComment(scalaOwner: ScDocCommentOwner, scalaDoc: ScDocComment, _) =>
        new ScalaDocContentWithSectionsGenerator(scalaOwner, scalaDoc, rendered = false).generate(builder)
      case _ =>
        val javadocContent = ScalaDocUtil.generateJavaDocInfoContentWithSections(actualComment.owner)
        builder.append(javadocContent)
    }
  }

  private def findActualComment(docOwner: PsiDocCommentOwner): Option[ActualComment] =
    docOwner.getDocComment match {
      case null =>
        findSuperElementWithDocComment(docOwner) match {
          case Some((base, baseComment)) => Some(ActualComment(base, baseComment, isInherited = true))
          case _ => None
        }
      case docComment =>
        Some(ActualComment(docOwner, docComment, isInherited = false))
    }

  private def findSuperElementWithDocComment(docOwner: PsiDocCommentOwner): Option[(PsiDocCommentOwner, PsiDocComment)] =
    docOwner match {
      case method: PsiMethod => findSuperMethodWithDocComment(method)
      case _                 => None
    }

  private def findSuperMethodWithDocComment(method: PsiMethod): Option[(PsiMethod, PsiDocComment)] =
    method.superMethods.map(base => (base, base.getDocComment)).find(_._2 != null)

  private def inheritedDisclaimer(clazz: PsiClass): String =
      s"""${DocumentationMarkup.CONTENT_START}
         |<b>Description copied from class: </b>
         |${HtmlPsiUtils.psiElementLink(clazz.qualifiedName, clazz.name)}
         |${DocumentationMarkup.CONTENT_END}""".stripMargin
}
