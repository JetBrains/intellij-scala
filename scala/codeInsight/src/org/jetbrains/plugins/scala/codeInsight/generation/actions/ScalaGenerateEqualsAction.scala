package org.jetbrains.plugins.scala.codeInsight.generation.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi.{PsiAnonymousClass, PsiFile}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.ScalaCodeInsightActionHandler
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.codeInsight.generation._
import org.jetbrains.plugins.scala.extensions.{PsiModifierListOwnerExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.OptionalBracesCode._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createMethodWithContext
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScType, TermSignature}
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil._
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectExt, ScalaFeatures}

final class ScalaGenerateEqualsAction extends ScalaBaseGenerateAction(
  new ScalaGenerateEqualsAction.Handler,
  ScalaCodeInsightBundle.message("generate.equals.and.hashcode.methods.action.text"),
  ScalaCodeInsightBundle.message("generate.equals.and.hashcode.methods.action.description")
)

object ScalaGenerateEqualsAction {

  private[generation] final class Handler extends ScalaCodeInsightActionHandler {

    private lazy val myEqualsFields = collection.mutable.LinkedHashSet[ScNamedElement]()
    private lazy val myHashCodeFields = collection.mutable.LinkedHashSet[ScNamedElement]()

    private def getUniqueLocalVarName(baseName: String): String = {
      val fieldNames = myHashCodeFields.map(_.name).toSet
      ScalaGenerateEqualsAction.getUniqueLocalVarName(baseName, fieldNames)
    }

    private def chooseOriginalMembers(aClass: ScClass)
                                     (implicit project: Project, editor: Editor): Boolean = {
      val equalsMethod = hasEquals(aClass)
      val hashCodeMethod = hasHashCode(aClass)
      var needEquals = equalsMethod.isEmpty
      var needHashCode = hashCodeMethod.isEmpty

      if (!needEquals && !needHashCode) {
        val text: String =
          if (aClass.isInstanceOf[PsiAnonymousClass]) JavaBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
          else JavaBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.qualifiedName)
        if (Messages.showYesNoDialog(project, text, JavaBundle.message("generate.equals.and.hashcode.already.defined.title"), Messages.getQuestionIcon) == DialogWrapper.OK_EXIT_CODE) {
          val deletedOk = inWriteAction {
            try {
              equalsMethod.get.delete()
              hashCodeMethod.get.delete()
              true
            }
            catch {
              case _: IncorrectOperationException =>
                false
            }
          }

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

    private def createHashCode(aClass: ScClass)(implicit ctx: ProjectContext, features: ScalaFeatures): ScFunction = {
      val declText = "def hashCode(): Int"
      val signature = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = 0", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val superCall = Option(if (!overridesFromJavaObject(aClass, signature)) "super.hashCode()" else null)
      val fieldNames = myHashCodeFields.map(_.name)
      val stateParts = superCall ++ fieldNames
      val stateText = stateParts.mkString("Seq(", ", ", ")")
      val stateValName = getUniqueLocalVarName("state")
      val firstStmtText = s"val $stateValName = $stateText"
      val arrow = ScalaPsiUtil.functionArrow(ctx)
      val calculationText = s"$stateValName.map(_.hashCode()).foldLeft(0)((a, b) $arrow 31 * a + b)"
      val methodText =
        optBraces"""override $declText =$BlockStart
                   |  $firstStmtText
                   |  $calculationText$BlockEnd""".stripMargin.replace("\r", "")
      createMethodWithContext(methodText, aClass, aClass.extendsBlock)
    }

    private def createCanEqual(aClass: ScClass): ScFunction = {
      val otherParamName = getUniqueLocalVarName("other")
      val declText = s"def canEqual($otherParamName: Any): Boolean"
      val sign = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = true", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val modifiers = if (needsOverrideModifier(aClass, sign)) ScalaKeyword.OVERRIDE else ScalaKeyword.PRIVATE
      val text = s"$modifiers $declText = $otherParamName.isInstanceOf[${aClass.name}]"
      createMethodWithContext(text, aClass, aClass.extendsBlock)
    }

    private def createEquals(aClass: ScClass)(implicit ctx: ProjectContext, features: ScalaFeatures): ScFunction = {
      val thatValName = getUniqueLocalVarName("that")
      val otherParamName = getUniqueLocalVarName("other")
      val fieldComparisons = myEqualsFields.map(_.name).map(name => s"$name == $thatValName.$name")
      val declText = s"def equals($otherParamName: Any): Boolean"
      val signature = new PhysicalMethodSignature(
        createMethodWithContext(declText + " = false", aClass, aClass.extendsBlock),
        ScSubstitutor.empty)
      val superCheck = Option(if (!overridesFromJavaObject(aClass, signature)) s"super.equals($thatValName)" else null)
      val canEqualCheck = Option(if (aClass.hasFinalModifier) null else s"$thatValName.canEqual(this)")
      val allChecks = superCheck ++ canEqualCheck ++ fieldComparisons
      val checksText = allChecks.mkString(" &&\n")
      val arrow = ScalaPsiUtil.functionArrow(ctx)
      val text =
        optBraces"""override $declText = $otherParamName match$BlockStart
                   |  case $thatValName: ${aClass.name} $arrow
                   |    $checksText
                   |  case _ $arrow false$BlockEnd""".stripMargin.replace("\r", "")
      createMethodWithContext(text, aClass, aClass.extendsBlock)
    }

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return

      try {
        val aClass: ScClass = findClassAtCaret(editor, file).getOrElse(return)
        val isOk = chooseOriginalMembers(aClass)(project, editor)
        if (!isOk) return

        implicit val projectContext: ProjectContext = project
        implicit val features: ScalaFeatures = aClass

        inWriteAction {
          val needHashCode = hasHashCode(aClass).isEmpty
          val hashCodeMethod = Option(
            if (needHashCode) createHashCode(aClass) else null)

          val needEquals = hasEquals(aClass).isEmpty
          val equalsMethod = Option(
            if (needEquals) createEquals(aClass) else null)

          val needCanEqual = needEquals && hasCanEqual(aClass).isEmpty && !aClass.hasFinalModifier
          val canEqualMethod = Option(
            if (needCanEqual) createCanEqual(aClass) else null)

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
      val stdTypes = clazz.getProject.stdTypes
      import stdTypes.{Any, Boolean}

      findSuchMethod(clazz, "equals", Boolean, Seq(Any))
    }

    private def hasHashCode(clazz: ScClass): Option[ScFunction] = {
      val stdTypes = clazz.getProject.stdTypes
      import stdTypes.Int

      findSuchMethod(clazz, "hashCode", Int, Seq.empty)
    }

    private def hasCanEqual(clazz: ScClass): Option[ScFunction] = {
      val stdTypes = clazz.getProject.stdTypes
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

    private def needsOverrideModifier(definition: ScTemplateDefinition, signature: TermSignature): Boolean = {
      val signatures = methodSignaturesToOverride(definition)
      val overriddenSignature = signatures.find(_.equiv(signature))
      overriddenSignature.isDefined
    }

    private def overridesFromJavaObject(definition: ScTemplateDefinition, signature: TermSignature): Boolean =
      methodSignaturesToOverride(definition).exists { sign =>
        sign.equiv(signature) &&
          //used only for equals and hashcode methods
          !sign.isScala && sign.method.findSuperMethods(false).isEmpty
      }

    private def findClassAtCaret(implicit editor: Editor, file: PsiFile) =
      elementOfTypeAtCaret(classOf[ScClass])
  }

  private def getUniqueLocalVarName(baseName: String, existingNames: Set[String]): String = {
    def clashes(name: String): Boolean = existingNames.contains(name)

    if (clashes(baseName)) {
      //1000 is used as a random big number, we don't expect that classes will have so much fields
      val candidates = (1 to 1000).iterator.map(baseName + _)
      candidates.find(newName => !clashes(newName)).getOrElse(baseName)
    }
    else
      baseName
  }
}
