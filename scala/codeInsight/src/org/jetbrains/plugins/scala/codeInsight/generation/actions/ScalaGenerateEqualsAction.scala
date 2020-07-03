package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.openapi.util.Computable
import com.intellij.psi.{PsiAnonymousClass, PsiFile}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMethodWithContext
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScType, TermSignature}
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Nikolay.Tropin
  * 8/19/13
  */
final class ScalaGenerateEqualsAction extends ScalaBaseGenerateAction(
  new ScalaGenerateEqualsAction.Handler,
  ScalaCodeInsightBundle.message("generate.equals.and.hashcode.methods.action.text"),
  ScalaCodeInsightBundle.message("generate.equals.and.hashcode.methods.action.description")
)

object ScalaGenerateEqualsAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    private lazy val myEqualsFields = collection.mutable.LinkedHashSet[ScNamedElement]()
    private lazy val myHashCodeFields = collection.mutable.LinkedHashSet[ScNamedElement]()

    private def chooseOriginalMembers(aClass: ScClass)
                                     (implicit project: Project, editor: Editor): Boolean = {
      val equalsMethod = hasEquals(aClass)
      val hashCodeMethod = hasHashCode(aClass)
      var needEquals = equalsMethod.isEmpty
      var needHashCode = hashCodeMethod.isEmpty

      if (!needEquals && !needHashCode) {
        val text: String =
          if (aClass.isInstanceOf[PsiAnonymousClass]) CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
          else CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.qualifiedName)
        if (Messages.showYesNoDialog(project, text, CodeInsightBundle.message("generate.equals.and.hashcode.already.defined.title"), Messages.getQuestionIcon) == DialogWrapper.OK_EXIT_CODE) {
          val deletedOk = ApplicationManager.getApplication.runWriteAction(new Computable[Boolean] {
            override def compute: Boolean = {
              try {
                equalsMethod.get.delete()
                hashCodeMethod.get.delete()
                true
              }
              catch {
                case _: IncorrectOperationException => false
              }
            }
          })
          if (!deletedOk) return false
          else {
            needEquals = true
            needHashCode = true
          }
        }
        else return false
      }

      fields(aClass) match {
        case Seq() =>
          HintManager.getInstance.showErrorHint(editor, ScalaCodeInsightBundle.message("no.fields.to.include.in.equals.hashcode.have.been.found"))
          false
        case fields if ApplicationManager.getApplication.isUnitTestMode =>
          myEqualsFields ++= fields
          myHashCodeFields ++= fields.filterNot(isVar)
          true
        case _ =>
          new ui.ScalaGenerateEqualsWizard(aClass, needEquals, needHashCode) match {
            case wizard if wizard.showAndGet() =>
              myEqualsFields ++= wizard.equalsFields
              myHashCodeFields ++= wizard.hashCodeFields
              true
            case _ => false
          }
      }
    }

    private def cleanup(): Unit = {
      myEqualsFields.clear()
      myHashCodeFields.clear()
    }

    private def createHashCode(aClass: ScClass): ScFunction = {
      val declText = "def hashCode(): Int"
      val signature = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = 0", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val superCall = Option(if (!overridesFromJavaObject(aClass, signature)) "super.hashCode()" else null)
      val usedFields = superCall ++: myHashCodeFields.map(_.name)
      val stateText = usedFields.mkString("Seq(", ", ", ")")
      val firstStmtText = s"val state = $stateText"
      val arrow = ScalaPsiUtil.functionArrow(aClass.getProject)
      val calculationText = s"state.map(_.hashCode()).foldLeft(0)((a, b) $arrow 31 * a + b)"
      val methodText =
        s"""override $declText = {
           |  $firstStmtText
           |  $calculationText
           |}""".stripMargin.replace("\r", "")
      createMethodWithContext(methodText, aClass, aClass.extendsBlock)
    }

    private def createCanEqual(aClass: ScClass, project: Project): ScFunction = {
      implicit val ctx: ProjectContext = project

      val declText = "def canEqual(other: Any): Boolean"
      val sign = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = true", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val overrideMod = overrideModifier(aClass, sign)
      val text = s"$overrideMod $declText = other.isInstanceOf[${aClass.name}]"
      createMethodWithContext(text, aClass, aClass.extendsBlock)
    }

    private def createEquals(aClass: ScClass, project: Project): ScFunction = {
      val fieldComparisons = myEqualsFields.map(_.name).map(name => s"$name == that.$name")
      val declText = "def equals(other: Any): Boolean"
      val signature = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = false", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val superCheck = Option(if (!overridesFromJavaObject(aClass, signature)) "super.equals(that)" else null)
      val canEqualCheck = Option(if (aClass.hasFinalModifier) null else "(that canEqual this)")
      val allChecks = superCheck ++: canEqualCheck ++: fieldComparisons
      val checksText = allChecks.mkString(" &&\n")
      val arrow = ScalaPsiUtil.functionArrow(project)
      val text =
        s"""override $declText = other match {
           |  case that: ${aClass.name} $arrow
           |    $checksText
           |  case _ $arrow false
           |}""".stripMargin.replace("\r", "")
      createMethodWithContext(text, aClass, aClass.extendsBlock)
    }

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

      try {
        val aClass: ScClass = findClassAtCaret(editor, file).getOrElse(return)
        val isOk = chooseOriginalMembers(aClass)(project, editor)
        if (!isOk) return

        extensions.inWriteAction {
          val needHashCode = hasHashCode(aClass).isEmpty
          val hashCodeMethod = Option(
            if (needHashCode) createHashCode(aClass) else null)

          val needEquals = hasEquals(aClass).isEmpty
          val equalsMethod = Option(
            if (needEquals) createEquals(aClass, project) else null)

          val needCanEqual = needEquals && hasCanEqual(aClass).isEmpty && !aClass.hasFinalModifier
          val canEqualMethod = Option(
            if (needCanEqual) createCanEqual(aClass, project) else null)

          val newMethods = hashCodeMethod ++: equalsMethod ++: canEqualMethod ++: Nil
          addMembers(aClass, newMethods, editor.getDocument)
        }
      }
      finally {
        cleanup()
      }
    }

    override def startInWriteAction: Boolean = false

    override def isValidFor(editor: Editor, file: PsiFile): Boolean =
      super.isValidFor(editor, file) &&
        findClassAtCaret(editor, file).exists(!_.isCase)

    private def hasEquals(clazz: ScClass): Option[ScFunction] = {
      val stdTypes = clazz.projectContext.stdTypes
      import stdTypes.{Any, Boolean}

      findSuchMethod(clazz, "equals", Boolean, Seq(Any))
    }

    private def hasHashCode(clazz: ScClass): Option[ScFunction] = {
      val stdTypes = clazz.projectContext.stdTypes
      import stdTypes.Int

      findSuchMethod(clazz, "hashCode", Int, Seq.empty)
    }

    private def hasCanEqual(clazz: ScClass): Option[ScFunction] = {
      val stdTypes = clazz.projectContext.stdTypes
      import stdTypes.{Any, Boolean}

      findSuchMethod(clazz, "canEqual", Boolean, Seq(Any))
    }

    private def findSuchMethod(clazz: ScClass, name: String, returnType: ScType, paramTypes: Seq[ScType]): Option[ScFunction] = {
      def equivParamTypes(f: ScFunction): Boolean = {
        val funParamTypes = f.parameters.flatMap(_.`type`().toOption)
        funParamTypes.lengthCompare(paramTypes.size) == 0 && funParamTypes.zip(paramTypes).forall {
          case (t1, t2) => t1.equiv(t2)
        }
      }

      clazz.functions.find { fun =>
        fun.name == name && fun.returnType.exists(_.equiv(returnType)) && equivParamTypes(fun)
      }
    }

    private def overrideModifier(definition: ScTemplateDefinition, signature: TermSignature): String = {
      val needModifier = methodSignaturesToOverride(definition).exists {
        _.equiv(signature)
      }
      if (needModifier) ScalaKeyword.OVERRIDE else ""
    }

    private def overridesFromJavaObject(definition: ScTemplateDefinition, signature: TermSignature): Boolean =
      methodSignaturesToOverride(definition).exists { sign =>
        sign.equiv(signature) &&
          //used only for equals and hashcode methods
          sign.isJava && sign.method.findSuperMethods(false).isEmpty
      }

    private def findClassAtCaret(implicit editor: Editor, file: PsiFile) =
      elementOfTypeAtCaret(classOf[ScClass])
  }

}
