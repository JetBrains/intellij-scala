package org.jetbrains.plugins.scala
package testingSupport

import java.util.Properties

import com.intellij.codeInsight.{CodeInsightBundle, CodeInsightUtil}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi._
import com.intellij.refactoring.util.classMembers.MemberInfo
import com.intellij.testIntegration.TestFramework
import com.intellij.testIntegration.createTest.{CreateTestDialog, TestGenerator}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.FormatterUtil
import org.jetbrains.plugins.scala.lang.parser.parsing.statements.Def
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ExtractSuperUtil
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestConfigurationUtil}
import scala.collection.JavaConverters._

class ScalaTestGenerator extends TestGenerator {
  def generateTest(project: Project, d: CreateTestDialog): PsiElement = {
    postponeFormattingWithin(project) {
      inWriteAction {
        try {
          val file: PsiFile = generateTestInternal(project, d)
          file
        } catch {
          case _: IncorrectOperationException =>
            invokeLater {
              val message = CodeInsightBundle.message("intention.error.cannot.create.class.message", d.getClassName)
              val title = CodeInsightBundle.message("intention.error.cannot.create.class.title")
              Messages.showErrorDialog(project, message, title)
            }
            null
        }
      }
    }
  }

  override def toString: String = ScalaLanguage.INSTANCE.getDisplayName

  private def createTestFileFromTemplate(d: CreateTestDialog, project: Project): PsiFile = {
    //copy-paste from JavaTestGenerator
    val templateName = d.getSelectedTestFrameworkDescriptor match {
      case f: AbstractTestFramework => f.getTestFileTemplateName
      case _ => ScalaFileTemplateUtil.SCALA_CLASS
    }
    val fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(templateName)
    val defaultProperties = FileTemplateManager.getInstance(project).getDefaultProperties
    val properties = new Properties(defaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, d.getClassName)
    val targetClass = d.getTargetClass
    if (targetClass != null && targetClass.isValid) properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, targetClass.getQualifiedName)
    try {
      FileTemplateUtil.createFromTemplate(fileTemplate, d.getClassName, properties, d.getTargetDirectory) match {
        case file: PsiFile => file
        case _ => null
      }
    }
    catch {
      case _: Exception => null
    }
  }

  private def generateTestInternal(project: Project, d: CreateTestDialog): PsiFile = {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val file = createTestFileFromTemplate(d, project)
    if (file == null) return file
    val typeDefinition = file.depthFirst().filterByType[ScTypeDefinition].next()
    val fqName = d.getSuperClassName
    if (fqName != null) {
      val psiClass = ElementScope(project).getCachedClass(fqName)
      addSuperClass(typeDefinition, psiClass, fqName)
    }
    val positionElement = typeDefinition.extendsBlock.templateBody.map(_.getFirstChild).getOrElse(typeDefinition)
    val editor: Editor = CodeInsightUtil.positionCursor(project, file, positionElement)
    addTestMethods(editor, typeDefinition, d.getSelectedTestFrameworkDescriptor, d.getSelectedMethods, d
      .shouldGeneratedBefore, d.shouldGeneratedAfter, d.getClassName)
    file
  }

  private def addSuperClass(typeDefinition: ScTypeDefinition, psiClass: Option[PsiClass], fqName: String) = {
    val extendsBlock = typeDefinition.extendsBlock

    def addExtendsRef(refName: String) = {
      val (extendsToken, classParents) = createClassTemplateParents(refName)(typeDefinition.getManager)
      val extendsAdded = extendsBlock.addBefore(extendsToken, extendsBlock.getFirstChild)
      val res = extendsBlock.addAfter(classParents, extendsAdded)
      extendsBlock.addBefore(createWhitespace(extendsAdded.getProject), res)
      res
    }

    psiClass match {
      case Some(cls) =>
        val classParents = addExtendsRef(cls.name)
        classParents.depthFirst().filterByType[ScStableCodeReferenceElement].next().bindToElement(cls)
      case None =>
        addExtendsRef(fqName)
    }
  }

  private def addTestMethods(editor: Editor, typeDef: ScTypeDefinition, testFramework: TestFramework, methods: java.util.Collection[MemberInfo],
                             generateBefore: Boolean, generateAfter: Boolean, className: String): Unit = {
    val templateBody = typeDef.extendsBlock.templateBody
    import TestConfigurationUtil.isInheritor
    import typeDef.projectContext

    implicit val normalIndent: String = FormatterUtil.getNormalIndentString(projectContext)

    import ScalaTestGenerator._
    templateBody match {
      case Some(body) =>
        val methodsList = methods.asScala.toList
        if (isInheritor(typeDef, "org.scalatest.FeatureSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.FeatureSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestFeatureSpecMethods(methodsList, body)
        } else if (isInheritor(typeDef, "org.scalatest.FlatSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.FlatSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestFlatSpecMethods(methodsList, body, className)
        } else if (isInheritor(typeDef, "org.scalatest.FreeSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.FreeSpecLike") ||
          isInheritor(typeDef, "org.scalatest.path.FreeSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestFreeSpecMethods(methodsList, body)
        } else if (isInheritor(typeDef, "org.scalatest.FunSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.FunSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestFunSpecMethods(methodsList, body, className)
        } else if (isInheritor(typeDef, "org.scalatest.FunSuiteLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.FunSuiteLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestFunSuiteMethods(methodsList, body)
        } else if (isInheritor(typeDef, "org.scalatest.PropSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.PropSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestPropSpecMethods(methodsList, body)
        } else if (isInheritor(typeDef, "org.scalatest.WordSpecLike") ||
          isInheritor(typeDef, "org.scalatest.fixture.WordSpecLike")) {
          generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef)
          addScalaTestWordSpecMethods(methodsList, body, className)
        } else if (isInheritor(typeDef, "org.specs2.specification.script.SpecificationLike")) {
          generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef)
          generateSpecs2ScriptSpecificationMethods(methodsList, body, className, typeDef)
        } else if (isInheritor(typeDef, "org.specs2.SpecificationLike")) {
          generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef)
          addSpecs2SpecificationMethods(methodsList, body, className)
        } else if (isInheritor(typeDef, "org.specs2.mutable.SpecificationLike")) {
          generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef)
          generateSpecs2MutableSpecificationMethods(methodsList, body, className)
        } else if (isInheritor(typeDef, "utest.framework.TestSuite")) {
          val file = typeDef.getContainingFile
          assert(file.isInstanceOf[ScalaFile])
          file.asInstanceOf[ScalaFile].addImportForPath("utest._")
          generateUTestMethods(methodsList, body, className)
        }
      case _ =>
    }
  }
}

object ScalaTestGenerator {

  private def withAnnotation(annotation: String, typeDef: ScTypeDefinition, body: ScTemplateBody)
                            (generateMethods: PsiElement => Unit)
                            (implicit elementScope: ElementScope): Unit =
    elementScope.getCachedClass(annotation).collect {
      case definition: ScTypeDefinition => definition
    }.foreach { clazz =>
      ExtractSuperUtil.addExtendsTo(typeDef, clazz)
      generateMethods(body.getLastChild)
    }

  private def generateScalaTestBeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean,
                                              typeDef: ScTypeDefinition): Unit = {
    import typeDef.{elementScope, projectContext}

    if (!(generateBefore || generateAfter)) return
    typeDef.extendsBlock.templateBody.foreach { body =>
      withAnnotation("org.scalatest.BeforeAndAfterEach", typeDef, body) { closingBrace =>
        if (generateBefore) {
          body.addBefore(createMethodFromText("override def beforeEach() {\n\n}"), closingBrace)
          body.addBefore(createNewLine(), closingBrace)
        }
        if (generateAfter) {
          body.addBefore(createMethodFromText("override def afterEach() {\n\n}"), closingBrace)
          body.addBefore(createNewLine(), closingBrace)
        }
      }
    }
  }

  private def generateSpecs2BeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean, typeDef: ScTypeDefinition): Unit = {
    import typeDef.{elementScope, projectContext}

    if (!(generateBefore || generateAfter)) return
    typeDef.extendsBlock.templateBody.foreach { body =>
      if (generateBefore) {
        withAnnotation("org.specs2.specification.BeforeEach", typeDef, body) { last =>
          body.addBefore(createMethodFromText("override protected def before: Any = {\n\n}"), last)
          body.addBefore(createNewLine(), last)
        }
      }
      if (generateAfter) {
        withAnnotation("org.specs2.specification.AfterEach", typeDef, body) { last =>
          body.addBefore(createMethodFromText("override protected def after: Any = {\n\n}"), last)
          body.addBefore(createNewLine(), last)
        }
      }
    }
  }

  private def addScalaTestFeatureSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      templateBody.addBefore(createExpressionFromText(
        methods.map("scenario (\"" + _.getMember.getName + "\"){\n\n}\n").
          fold("feature(\"Methods tests\") {")(_ + "\n" + _) + "}"),
        templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFlatSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      templateBody.addBefore(createExpressionFromText("behavior of \"" + className + "\""), closingBrace)
      templateBody.addBefore(createNewLine("\n\n"), closingBrace)
      methods.map("it should \"" + _.getMember.getName + "\" in {\n\n}").
        map(createExpressionFromText).
        foreach(expr => {
          templateBody.addBefore(expr, closingBrace)
          templateBody.addBefore(createNewLine("\n\n"), closingBrace)
        })
    }
  }

  private def addScalaTestFreeSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      templateBody.addBefore(createExpressionFromText(
        methods.map("\"" + _.getMember.getName + "\" in {\n\n}\n").fold("\"Methods tests\" - {")(_ + "\n" + _) + "\n}"), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      templateBody.addBefore(createExpressionFromText(
        methods.map("it(\"should " + _.getMember.getName + "\") {\n\n}\n").
          fold("describe(\"" + className + "\") {\n")(_ + "\n" + _) + "\n}"),
        templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSuiteMethods(methods: List[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      methods.map("test(\"test" + _.getMember.getName.capitalize + "\") {\n\n}").
        map(createExpressionFromText).
        foreach(expr => {
          templateBody.addBefore(expr, closingBrace)
          templateBody.addBefore(createNewLine("\n\n"), closingBrace)
        })
    }
  }

  private def addScalaTestPropSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      methods.map("property(\"" + _.getMember.getName + " property\"){\n\n}").
        map(createExpressionFromText).
        foreach(expr => {
          templateBody.addBefore(expr, closingBrace)
          templateBody.addBefore(createNewLine("\n\n"), closingBrace)
        })
    }
  }

  private def addScalaTestWordSpecMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      templateBody.addBefore(createExpressionFromText(
        methods.map("\"" + _.getMember.getName + "\" in {\n\n}\n").
          fold("\"" + className + "\" should {\n")(_ + "\n" + _) + "\n}"),
        templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addSpecs2SpecificationMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String)
                                           (implicit normalIndent: String): Unit = {
    import templateBody.projectContext

    val testNames = methods.map("test" + _.getMember.getName.capitalize)
    val doubleIndent = normalIndent + normalIndent

    val checkMethodsString = if (methods.nonEmpty) testNames.map(testName => doubleIndent + testName + " $" + testName).
      fold("\n" + normalIndent + "Methods of " + className + " should pass tests:")(_ + "\n" + _)
    else ""
    val closingBrace = templateBody.getLastChild
    templateBody.addBefore(createMethodFromText("def is = s2\"\"\"" + checkMethodsString +
      "\n" + normalIndent + "\"\"\""), closingBrace)
    templateBody.addBefore(createNewLine(), closingBrace)
    testNames.map { testName =>
      templateBody.addBefore(createMethodFromText("def " + testName + " = ok"), closingBrace)
      templateBody.addBefore(createNewLine(), closingBrace)
    }
  }

  private def generateSpecs2ScriptSpecificationMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String, typeDef: ScTypeDefinition)
                                                      (implicit normalIndent: String): Unit = {
    import templateBody.{elementScope, projectContext}

    withAnnotation("org.specs2.specification.Groups", typeDef, templateBody) { closingBrace =>
      val testNames = methods.map("test" + _.getMember.getName.capitalize)
      val doubleIndent = normalIndent + normalIndent
      val checkMethodsString = if (methods.nonEmpty) testNames.map(doubleIndent + "+ " + _).
        fold("\n" + normalIndent + "Methods of " + className + " should pass tests:")(_ + "\n" + _)
      else ""
      templateBody.addBefore(createMethodFromText("def is = s2\"\"\"" + checkMethodsString +
        "\n" + doubleIndent + "\"\"\""), closingBrace)
      templateBody.addBefore(createNewLine(), closingBrace)
      if (methods.nonEmpty) {
        templateBody.addBefore(createExpressionFromText(testNames.map("eg := ok //" + _).
          fold("\"" + className + "\" - new group {")(_ + "\n" + _) + "\n}"), closingBrace)
        templateBody.addBefore(createNewLine(), closingBrace)
      }
    }
  }

  private def generateSpecs2MutableSpecificationMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      templateBody.addBefore(createExpressionFromText(methods.
        map("\"" + _.getMember.getName + "\" in {\nok\n}\n").
        fold("\"" + className + "\" should {")(_ + "\n" + _) + "\n}"), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def generateUTestMethods(methods: List[MemberInfo], templateBody: ScTemplateBody, className: String)
                                  (implicit normalIndent: String): Unit = {
    import templateBody.projectContext

    templateBody.addBefore(createElement("val tests = TestSuite{}", Def.parse),
      templateBody.getLastChild)
    if (methods.nonEmpty) {
      templateBody.addBefore(createElement(methods.map(normalIndent + "\"" +
        _.getMember.getName + "\" - {}\n").fold("val methodsTests = TestSuite{")(_ + "\n" + _) + "}", Def.parse), templateBody.getLastChild)
    }
  }

}
