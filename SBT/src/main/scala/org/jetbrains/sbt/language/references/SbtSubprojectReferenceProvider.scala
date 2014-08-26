package org.jetbrains.sbt
package language.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * @author Nikolay Obedin
 * @since 8/26/14.
 */
class SbtSubprojectReferenceProvider extends PsiReferenceProvider {

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    if (element.getContainingFile.getFileType.getName != Sbt.Name) return Array.empty

    extractSubprojectPath(element).map { path =>
      FilenameIndex.getFilesByName(element.getProject, "build.sbt", GlobalSearchScope.allScope(element.getProject)).filter { file =>
        Option(file.getParent).map(_.getVirtualFile.getCanonicalPath).fold(false)(_.endsWith(path))
      }.map { file =>
        new SbtSubprojectReference(element, file)
      }.toSeq
    }.getOrElse(Seq.empty).toArray
  }

  private def extractSubprojectPath(element: PsiElement): Option[String] = {
    for {
      listOfPatterns <- Option(element.getParent)
      patternDef <- Option(listOfPatterns.getParent)
    } yield patternDef match {
      case ScPatternDefinition.expr(e) => e match {
        case call: ScMethodCall =>
          var result: Option[String] = None
          val visitor = new ScalaRecursiveElementVisitor {
            override def visitMethodCallExpression(call: ScMethodCall) = call match {
              case ScMethodCall(expr, Seq(_: ScLiteral, pathElt)) if expr.getText == "Project" =>
                result = extractPathFromParam(pathElt)
              case ScMethodCall(expr, Seq(pathElt)) if expr.getText.matches("^project.+?in$") =>
                result = extractPathFromParam(pathElt)
              case ScMethodCall(expr, _) if expr.getText.startsWith("project") =>
                result = Some(element.getText)
                super.visitMethodCallExpression(call)
              case _ =>
                super.visitMethodCallExpression(call)
            }
          }
          call.accept(visitor)
          result
        case expr: ScReferenceExpression => Some(element.getText)
        case _ => None
      }
      case _ => None
    }
  }.flatten

  // Extract path from file params like vars and `new File(...)` ctors
  private def extractPathFromParam(element: PsiElement): Option[String] = element match {
    case ScTemplateDefinition.ExtendsBlock(block) if element.isInstanceOf[ScNewTemplateDefinition] =>
      block.getChildren.toSeq.headOption match {
        case Some(classParent : ScClassParents) =>
          classParent.constructor.map { ctor =>
            ctor.args.map(_.exprs).collect {
              case Seq(pathLit : ScLiteral) if pathLit.isString =>
                pathLit.getValue.asInstanceOf[String]
            }
          }.flatten
        case _ => None
      }
    case _ => None
  }
}

private class SbtSubprojectReference[T <: PsiElement](val element: T, val sbtFile: PsiFile)
        extends PsiReferenceBase.Immediate[T](element,
          TextRange.create(element.getStartOffsetInParent, element.getStartOffsetInParent + element.getTextLength),
          sbtFile)