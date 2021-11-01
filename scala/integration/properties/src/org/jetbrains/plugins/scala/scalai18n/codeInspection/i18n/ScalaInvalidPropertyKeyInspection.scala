package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection._
import com.intellij.java.i18n.JavaI18nBundle
import com.intellij.lang.properties.PropertiesReferenceManager
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.Ref
import com.intellij.psi.{util => _, _}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.PsiMethodExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import java.util
import java.util.Objects
import scala.collection.mutable

class ScalaInvalidPropertyKeyInspection extends LocalInspectionTool {

  @Nullable override def checkFile(@NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val visitor: UnresolvedPropertyVisitor = new UnresolvedPropertyVisitor(manager, isOnTheFly)
    file.accept(visitor)
    val problems: util.List[ProblemDescriptor] = visitor.getProblems
    if (problems.isEmpty) null else problems.toArray(new Array[ProblemDescriptor](problems.size))
  }

  private object UnresolvedPropertyVisitor {
    def appendPropertyKeyNotFoundProblem(bundleName: String, @NotNull key: String,
                                         @NotNull expression: ScLiteral, @NotNull manager: InspectionManager,
                                         @NotNull problems: util.List[ProblemDescriptor], onTheFly: Boolean): Unit = {
      val description: String = JavaI18nBundle.message("inspection.unresolved.property.key.reference.message", key)
      problems.add(manager.createProblemDescriptor(expression, description, null: LocalQuickFix,
        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly))
    }

    @NotNull def filterNotInLibrary(@NotNull project: Project,
                                    @NotNull propertiesFiles: util.List[PropertiesFile]): util.List[PropertiesFile] = {
      val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
      val result: util.List[PropertiesFile] = new util.ArrayList[PropertiesFile](propertiesFiles.size)
      propertiesFiles.forEach { file =>
        if (!fileIndex.isInLibraryClasses(file.getVirtualFile) && !fileIndex.isInLibrarySource(file.getVirtualFile)) {
          result.add(file)
        }
      }
      result
    }

    def isComputablePropertyExpression(myExpression: ScExpression): Boolean = {
      var expression = myExpression
      while (expression != null && expression.getParent.isInstanceOf[ScParenthesisedExpr]) {
        expression = expression.getParent.asInstanceOf[ScExpression]
      }
      expression != null && expression.getParent.isInstanceOf[ScExpression]
    }
  }

  class UnresolvedPropertyVisitor(myManager: InspectionManager, onTheFly: Boolean) extends ScalaRecursiveElementVisitor {
    override def visitLiteral(expression: ScLiteral): Unit = {
      if (expression.isInstanceOf[ScInterpolatedStringLiteral]) return
      val value: AnyRef = expression.getValue
      if (value == null || !value.isInstanceOf[String]) return
      val key: String = value.asInstanceOf[String]
      if (UnresolvedPropertyVisitor.isComputablePropertyExpression(expression)) return
      val resourceBundleName: Ref[String] = new Ref[String]
      if (!ScalaI18nUtil.isValidPropertyReference(expression, key, resourceBundleName)) {
        UnresolvedPropertyVisitor.appendPropertyKeyNotFoundProblem(resourceBundleName.get, key,
          expression, myManager, myProblems, onTheFly)
      } else {
        expression.getParent match {
          case nvp: ScNameValuePair =>
            if (Objects.equals(nvp.getName, AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)) {
              val manager: PropertiesReferenceManager = PropertiesReferenceManager.getInstance(expression.getProject)
              val module: Module = ModuleUtilCore.findModuleForPsiElement(expression)
              if (module != null) {
                val propFiles: util.List[PropertiesFile] = manager.findPropertiesFiles(module, key)
                if (propFiles.isEmpty) {
                  val description: String = JavaI18nBundle.message("inspection.invalid.resource.bundle.reference", key)
                  val problem: ProblemDescriptor = myManager.createProblemDescriptor(expression, description, null.asInstanceOf[LocalQuickFix], ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly)
                  myProblems.add(problem)
                }
              }
            }
          case expressions: ScArgumentExprList if expression.getParent.getParent.isInstanceOf[ScMethodCall] =>
            val annotationParams = new mutable.HashMap[String, AnyRef]
            annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
            if (!ScalaI18nUtil.mustBePropertyKey(expression, annotationParams)) return
            val paramsCount: java.lang.Integer = ScalaI18nUtil.getPropertyValueParamsMaxCount(expression)
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
                      if (args(i) eq expression) {
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
                      i
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
