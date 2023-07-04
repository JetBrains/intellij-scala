package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.{PsiClass, PsiDocCommentOwner, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions.{&, PsiClassExt, PsiMemberExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScPatternDefinition, ScValueOrVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScEnum}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import java.net.URL
import scala.util.Try

object ScalaDocGenerator {

  private val Log = Logger.getInstance(this.getClass)

  private final case class ActualComment(
    owner: PsiDocCommentOwner,
    comment: PsiDocComment,
    isInherited: Boolean,
    focusOn: Option[ScParameter] = None
  )

  // IDEA doesn't log exceptions occurred during doc rendering,
  // we would like to at least show them in internal mode, during development
  private def internalLog[T](body: => T): T = Try(body).fold(
    {
      case ex: ProcessCanceledException => throw ex
      case ex: IndexNotReadyException => throw ex
      case ex =>
        if (ApplicationManager.getApplication.isInternal)
          Log.error("Unexpected exception occurred during doc info generation", ex)
        throw ex
    },
    identity
  )

  def generateDoc(elementWithDoc: PsiElement, originalElement: Option[PsiElement]): String = internalLog {
    val builder = new StringBuilder

    // for library classes, get class from sources jar
    val actualElementWithDoc0 = elementWithDoc.getNavigationElement
    val actualElementWithDoc =
      actualElementWithDoc0 match {
        case ScEnumCase.Original(originalEnumCase) => originalEnumCase
        case ScEnum.Original(originalEnum)         => originalEnum
        case _                                     => actualElementWithDoc0
      }

    appendHeader(builder, actualElementWithDoc)

    ScalaDocDefinitionGenerator.generate(builder, actualElementWithDoc, originalElement)
    generateDocContent(builder, actualElementWithDoc)

    appendFooter(builder)

    builder.result()
  }

  def generateDocRendered(commentOwner: ScDocCommentOwner, comment: ScDocComment): String = internalLog {
    val builder = new StringBuilder

    appendHeader(builder, commentOwner)
    new ScalaDocContentWithSectionsGenerator(commentOwner, comment, rendered = true).generate(builder)
    appendFooter(builder)

    builder.result()
  }

  private def appendHeader(builder: StringBuilder, actualElementWithDoc: PsiElement): Unit = {
    builder.append("<html>")
    builder.append("<head>")
    builder.append("<style>").append(ScalaDocCss.value).append("</style>")
    baseUrl(actualElementWithDoc).foreach { url =>
      // used to resolve URLs of local images (see com.intellij.codeInsight.javadoc.JavaDocInfoGenerator.getBaseUrl)
      builder.append(s"""<base href="$url">""")
    }
    builder.append("</head>")
    builder.append("<body>")
  }

  private def appendFooter(builder: StringBuilder): Unit = {
    builder.append("</body>")
    builder.append("</html>")
  }

  private def baseUrl(element: PsiElement): Option[URL] =
    for {
      file  <- Option(element.getContainingFile)
      vFile <- Option(file.getVirtualFile)
    } yield VfsUtilCore.convertToURL(vFile.getUrl)

  private def generateDocContent(builder: StringBuilder, e: PsiElement): Unit =
    for {
      (commentOwner, param)  <- getCommentOwner(e)
      actualComment          <- findActualComment(commentOwner, param)
    } yield generateDocComment(builder, actualComment)

  private def generateDocComment(builder: StringBuilder, actualComment: ActualComment): Unit = {
    if (actualComment.isInherited)
      builder.append(inheritedDisclaimer(actualComment.owner.containingClass))

    actualComment match {
      case ActualComment(scalaOwner: ScDocCommentOwner, scalaDoc: ScDocComment, _, Some(param)) =>
        new ScalaDocContentWithSectionsGenerator(scalaOwner, scalaDoc, rendered = false).generateForParam(builder, param)
      case ActualComment(scalaOwner: ScDocCommentOwner, scalaDoc: ScDocComment, _, None) =>
        new ScalaDocContentWithSectionsGenerator(scalaOwner, scalaDoc, rendered = false).generate(builder)
      case _ =>
        val javadocContent = ScalaDocUtil.generateJavaDocInfoContentWithSections(actualComment.owner)
        builder.append(javadocContent)
    }
  }

  private def getCommentOwner(element: PsiElement): Option[(PsiDocCommentOwner, Option[ScParameter])] = {
    def findDocOwner(e: PsiElement): Option[PsiDocCommentOwner] =
      e match {
        case pattern: ScBindingPattern =>
          pattern.nameContext match {
            case (definition: ScValueOrVariable) & (_: ScPatternDefinition | _: ScVariableDefinition) => Some(definition)
            case _ => None
          }
        case owner: PsiDocCommentOwner => Some(owner)
        case _ => None
      }

    element match {
      case param: ScParameter =>
        findDocOwner(param.getDeclarationScope).map((_, Some(param)))
      case enumCase: ScEnumCase =>
        //in the psi structure the actual doc comment belongs to ScEnumCases not ScEnumCase
        Some(enumCase.enumCases, None)
      case _ =>
        findDocOwner(element).map((_, None))
    }
  }

  private def findActualComment(docOwner: PsiDocCommentOwner, param: Option[ScParameter]): Option[ActualComment] =
    docOwner.getDocComment match {
      case null =>
        findSuperElementWithDocComment(docOwner) match {
          case Some((base, baseComment)) => Some(ActualComment(base, baseComment, isInherited = true, param))
          case _ => None
        }
      case docComment =>
        Some(ActualComment(docOwner, docComment, isInherited = false, param))
    }

  private def findSuperElementWithDocComment(docOwner: PsiDocCommentOwner): Option[(PsiDocCommentOwner, PsiDocComment)] =
    docOwner match {
      case method: ScFunction => findSuperMethodWithDocComment(method)
      case _                  => None
    }

  private def findSuperMethodWithDocComment(method: ScFunction): Option[(PsiMethod, PsiDocComment)] = {
    val supers = method.superMethods
    val supersDocs = supers.iterator.map(selectMethodFromSources).map(base => (base, base.getDocComment))
    supersDocs.find(_._2 != null)
  }

  private def selectMethodFromSources(method: PsiMethod): PsiMethod =
    method.getNavigationElement match {
      case m: PsiMethod => m
      case _            => method
    }

  private def inheritedDisclaimer(clazz: PsiClass): String =
    s"""${DocumentationMarkup.CONTENT_START}
       |<b>Description copied from class: </b>
       |${HtmlPsiUtils.psiElementLink(clazz.qualifiedName, clazz.name)}
       |${DocumentationMarkup.CONTENT_END}""".stripMargin
}