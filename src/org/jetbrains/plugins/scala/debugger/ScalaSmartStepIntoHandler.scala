package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.actions.{JvmSmartStepIntoHandler, SmartStepTarget, MethodSmartStepTarget}
import com.intellij.debugger.SourcePosition
import java.util.{List => JList, Collections}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil
import com.intellij.psi._
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import scala.collection.JavaConverters._
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import scala.Some
import scala.Int
import scala.Boolean
import com.intellij.util.Range

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.12
 */

class ScalaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  override def findSmartStepTargets(position: SourcePosition): JList[SmartStepTarget] = {
    val line: Int = position.getLine
    if (line < 0) {
      return Collections.emptyList[SmartStepTarget]
    }
    val file: PsiFile = position.getFile
    val vFile: VirtualFile = file.getVirtualFile
    if (vFile == null) {
      return Collections.emptyList[SmartStepTarget]
    }
    val doc: Document = FileDocumentManager.getInstance.getDocument(vFile)
    if (doc == null) return Collections.emptyList[SmartStepTarget]
    if (line >= doc.getLineCount) {
      return Collections.emptyList[SmartStepTarget]
    }
    val startOffset: Int = doc.getLineStartOffset(line)
    val offset: Int = CharArrayUtil.shiftForward(doc.getCharsSequence, startOffset, " \t")
    val element: PsiElement = file.findElementAt(offset)

    if (element != null) {
      val lines: Range[Integer] = new Range[Integer](doc.getLineNumber(element.getTextOffset), doc.getLineNumber(element.getTextOffset + element.getTextLength))
      val targets: List[SmartStepTarget] = findReferencedMethodsScala(position).map { method =>
        new MethodSmartStepTarget(method, null, null, false, lines)
      }
      return targets.asJava
    }
    Collections.emptyList[SmartStepTarget]
  }

  def isAvailable(position: SourcePosition): Boolean = {
    val file: PsiFile = position.getFile
    file.isInstanceOf[ScalaFile]
  }

  private def findReferencedMethodsScala(position: SourcePosition): List[PsiMethod] = {
    val line: Int = position.getLine
    if (line < 0) return List.empty
    val file: PsiFile = position.getFile
    if (!file.isInstanceOf[ScalaFile]) return List.empty
    val scalaFile = file.asInstanceOf[ScalaFile]
    if (scalaFile.isCompiled) return List.empty
    val vFile: VirtualFile = file.getVirtualFile
    if (vFile == null) return List.empty
    val document: Document = FileDocumentManager.getInstance.getDocument(vFile)
    if (document == null) return List.empty
    if (line >= document.getLineCount) return List.empty
    val startOffset: Int = document.getLineStartOffset(line)
    val lineRange: TextRange = new TextRange(startOffset, document.getLineEndOffset(line))
    val offset: Int = CharArrayUtil.shiftForward(document.getCharsSequence, startOffset, " \t")
    var element: PsiElement = file.findElementAt(offset)
    if (element == null) return List.empty
    while (element.getParent != null && element.getParent.getTextRange.getStartOffset >= lineRange.getStartOffset) {
      element = element.getParent
    }
    val methods: HashSet[PsiMethod] = new HashSet[PsiMethod]()

    class MethodsVisitor extends ScalaRecursiveElementVisitor {
      override def visitExpression(expr: ScExpression) {
        val tuple = (expr.getImplicitConversions()._1, expr.getImplicitConversions()._2)
        tuple match {
          case (_, Some(f: PsiMethod)) => methods += f
          case (_, Some(t: ScTypedStmt)) => t.getType(TypingContext.empty).getOrAny match {
            case f@ScFunctionType(_, _) =>
              f match {
                case ScParameterizedType(ScDesignatorType(funTrait: ScTrait), _) =>
                  ScType.extractClass(t.getType(TypingContext.empty).get) match {
                    case Some(clazz: ScTypeDefinition) =>
                      val funApply = funTrait.functionsByName("apply").apply(0)
                      clazz.allMethods.foreach((signature: PhysicalSignature) => {
                        signature.method match {
                          case fun: ScFunction if fun.name == "apply" && fun.superMethods.contains(funApply) =>
                            methods += fun
                          case _ =>
                        }
                      })
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
          case _ =>
        }

        expr match {
          case n: ScNewTemplateDefinition if n.extendsBlock.templateBody != None =>
            return //ignore anonymous classes
          case n: ScNewTemplateDefinition =>
            n.extendsBlock.templateParents match {
              case Some(tp) =>
                tp.typeElements.headOption match {
                  case Some(te) =>
                    val constr = te match {
                      case p: ScParameterizedTypeElement => p.findConstructor
                      case s: ScSimpleTypeElement => s.findConstructor
                      case _ => None
                    }
                    constr match {
                      case Some(constr) =>
                        constr.reference match {
                          case Some(ref) =>
                            ref.bind() match {
                              case Some(ScalaResolveResult(f: PsiMethod, _)) => methods += f
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
              case _ =>
            }
          case expr if ScUnderScoreSectionUtil.isUnderscoreFunction(expr) =>
            return //ignore clojures
          case ref: ScReferenceExpression =>
            ref.resolve() match {
              case fun: PsiMethod => methods += fun
              case _ =>
            }
          case f: ScForStatement =>
            f.getDesugarizedExpr match {
              case Some(expr) =>
                expr.accept(new MethodsVisitor)
                return
              case _ =>
            }
          case f: ScFunctionExpr =>
            return //ignore closures
          case b: ScBlock if b.isAnonymousFunction =>
            return //ignore closures
          case _ =>
        }
        super.visitExpression(expr)
      }
    }

    val methodCollector: PsiElementVisitor = new MethodsVisitor
    element.accept(methodCollector)

    element = element.getNextSibling
    while (element != null && !lineRange.intersects(element.getTextRange)) {
      element.accept(methodCollector)
      element = element.getNextSibling
    }
    methods.toList
  }
}