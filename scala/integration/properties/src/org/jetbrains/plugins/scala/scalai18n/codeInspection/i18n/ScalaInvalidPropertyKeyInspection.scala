package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection._
import com.intellij.java.i18n.JavaI18nBundle
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.util.Ref
import com.intellij.psi.{util => _, _}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import java.util
import java.util.Objects
import scala.collection.mutable

//noinspection InstanceOf
class ScalaInvalidPropertyKeyInspection extends LocalInspectionTool {

  @Nullable
  override def checkFile(
    @NotNull file: PsiFile,
    @NotNull manager: InspectionManager,
    isOnTheFly: Boolean
  ): Array[ProblemDescriptor] = {
    val visitor: UnresolvedPropertyVisitor = new UnresolvedPropertyVisitor(manager, isOnTheFly)
    file.accept(visitor)
    val problems: util.List[ProblemDescriptor] = visitor.getProblems
    if (problems.isEmpty) null else problems.toArray(new Array[ProblemDescriptor](problems.size))
  }

  private object UnresolvedPropertyVisitor {
    def appendPropertyKeyNotFoundProblem(
      @NotNull key: String,
      @NotNull expression: ScLiteral,
      @NotNull manager: InspectionManager,
      @NotNull problems: util.List[ProblemDescriptor],
      onTheFly: Boolean
    ): Unit = {
      val description: String = JavaI18nBundle.message("inspection.unresolved.property.key.reference.message", key)
      problems.add(manager.createProblemDescriptor(expression, description, null: LocalQuickFix,
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly))
    }
  }

  private class UnresolvedPropertyVisitor(myManager: InspectionManager, onTheFly: Boolean) extends ScalaRecursiveElementVisitor {
    import UnresolvedPropertyVisitor._

    override def visitLiteral(literal: ScLiteral): Unit = {
      val stringLiteral: ScStringLiteral = literal match {
        case _: ScInterpolatedStringLiteral => return
        case sl: ScStringLiteral            => sl
        case _                              => return
      }

      val key: String = stringLiteral.getValue
      val resourceBundleName: Ref[String] = new Ref[String]
      if (!ScalaI18nUtil.isValidPropertyReference(stringLiteral, key, resourceBundleName)) {
        appendPropertyKeyNotFoundProblem(key, stringLiteral, myManager, myProblems, onTheFly)
      } else {
        stringLiteral.getParent match {
          case ScAssignment(left: ScReferenceExpression, Some(`stringLiteral`)) =>
            if (Objects.equals(left.refName, AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
              val manager: PropertiesReferenceManager = PropertiesReferenceManager.getInstance(stringLiteral.getProject)
              val module: Module = ModuleUtilCore.findModuleForPsiElement(stringLiteral)
              if (module != null) {
                val propFiles: util.List[PropertiesFile] = manager.findPropertiesFiles(module, key)
                if (propFiles.isEmpty) {
                  val description: String = JavaI18nBundle.message("inspection.invalid.resource.bundle.reference", key)
                  val problem: ProblemDescriptor = myManager.createProblemDescriptor(stringLiteral, description, null.asInstanceOf[LocalQuickFix], ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly)
                  myProblems.add(problem)
                }
              }
            }
          case expressions: ScArgumentExprList if stringLiteral.getParent.getParent.isInstanceOf[ScMethodCall] =>
            val annotationParams = new mutable.HashMap[String, AnyRef]
            annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
            if (!ScalaI18nUtil.mustBePropertyKey(stringLiteral, annotationParams)) return
            val paramsCount: java.lang.Integer = ScalaI18nUtil.getPropertyValueParamsMaxCount(stringLiteral)
            if (paramsCount == -1) return
            val methodCall: ScMethodCall = expressions.getParent.asInstanceOf[ScMethodCall]
            methodCall.getInvokedExpr match {
              case referenceExpression: ScReferenceExpression =>
                referenceExpression.resolve() match {
                  case method: PsiMethod =>
                    val args = expressions.exprs
                    var i: Int = 0
                    var flag = true
                    while (i < args.length && flag) {
                      if (args(i) eq stringLiteral) {
                        val param: java.lang.Integer = args.length - i - 1
                        val parameters = method.parameters
                        if (i + paramsCount >= args.length && method != null &&
                          method.getParameterList.getParametersCount == i + 2 &&
                          parameters(i + 1).isVarArgs) {
                          myProblems.add(myManager.createProblemDescriptor(methodCall,
                            JavaI18nBundle.message("property.has.more.parameters.than.passed", key, paramsCount, param),
                            onTheFly, new Array[LocalQuickFix](0), ProblemHighlightType.GENERIC_ERROR))
                        }
                        flag = false
                      }
                      i += 1
                    }
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      }
    }

    def getProblems: util.List[ProblemDescriptor] = myProblems

    private final val myProblems: util.List[ProblemDescriptor] = new util.ArrayList[ProblemDescriptor]
  }

}
