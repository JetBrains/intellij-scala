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
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ExtractSuperUtil
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestConfigurationUtil}
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.collection.JavaConverters._

class ScalaTestGenerator extends TestGenerator {

  override def generateTest(project: Project, d: CreateTestDialog): PsiElement = {
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

  private def createTestFileFromTemplate(dialog: CreateTestDialog, project: Project): PsiFile = {
    //copy-paste from JavaTestGenerator
    val templateName = dialog.getSelectedTestFrameworkDescriptor match {
      case f: AbstractTestFramework => f.testFileTemplateName
      case _ => ScalaFileTemplateUtil.SCALA_CLASS
    }
    val fileTemplate = FileTemplateManager.getInstance(project).getCodeTemplate(templateName)
    val defaultProperties = FileTemplateManager.getInstance(project).getDefaultProperties
    val properties = new Properties(defaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, dialog.getClassName)
    val targetClass = dialog.getTargetClass
    if (targetClass != null && targetClass.isValid)
      properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, targetClass.getQualifiedName)
    try {
      FileTemplateUtil.createFromTemplate(fileTemplate, dialog.getClassName, properties, dialog.getTargetDirectory) match {
        case file: PsiFile => file
        case _ => null
      }
    }
    catch {
      case _: Exception => null
    }
  }

  private def generateTestInternal(project: Project, dialog: CreateTestDialog): PsiFile = {
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val file = createTestFileFromTemplate(dialog, project)
    if (file == null) return file
    val typeDefinition = file.depthFirst().instancesOf[ScTypeDefinition].next()
    val fqName = dialog.getSuperClassName
    if (fqName != null) {
      val psiClass = ElementScope(project).getCachedClass(fqName)
      addSuperClass(typeDefinition, psiClass, fqName)
    }
    val positionElement = typeDefinition.extendsBlock.templateBody.map(_.getFirstChild).getOrElse(typeDefinition)
    val editor: Editor = CodeInsightUtil.positionCursor(project, file, positionElement)
    addTestMethods(
      editor,
      typeDefinition,
      dialog.getSelectedTestFrameworkDescriptor,
      dialog.getSelectedMethods.asScala.toSeq,
      dialog.shouldGeneratedBefore,
      dialog.shouldGeneratedAfter,
      dialog.getClassName
    )
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
        classParents.depthFirst().instancesOf[ScStableCodeReference].next().bindToElement(cls)
      case None =>
        addExtendsRef(fqName)
    }
  }


  private def addTestMethods(
    editor: Editor,
    typeDef: ScTypeDefinition,
    testFramework: TestFramework,
    methods: Seq[MemberInfo],
    generateBefore: Boolean,
    generateAfter: Boolean,
    className: String
  ): Unit = {
    val body = typeDef.extendsBlock.templateBody.getOrElse(return)

    import ScalaTestGenerator._
    import TestConfigurationUtil.isInheritor

    if (isInheritor(typeDef, "org.scalatest.FeatureSpecLike", "org.scalatest.fixture.FeatureSpecLike", "org.scalatest.featurespec.AnyFeatureSpecLike", "org.scalatest.featurespec.FixtureAnyFeatureSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestFeatureSpecMethods(methods, body)
    } else if (isInheritor(typeDef, "org.scalatest.FlatSpecLike", "org.scalatest.fixture.FlatSpecLike", "org.scalatest.flatspec.AnyFlatSpecLike", "org.scalatest.flatspec.FixtureAnyFlatSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestFlatSpecMethods(methods, body, className)
    } else if (isInheritor(typeDef, "org.scalatest.FreeSpecLike", "org.scalatest.fixture.FreeSpecLike", "org.scalatest.path.FreeSpecLike", "org.scalatest.freespec.AnyFreeSpecLike", "org.scalatest.freespec.FixtureAnyFreeSpecLike", "org.scalatest.freespec.PathAnyFreeSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestFreeSpecMethods(methods, body)
    } else if (isInheritor(typeDef, "org.scalatest.FunSpecLike", "org.scalatest.fixture.FunSpecLike", "org.scalatest.funspec.AnyFunSpecLike", "org.scalatest.funspec.FixtureAnyFunSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestFunSpecMethods(methods, body, className)
    } else if (isInheritor(typeDef, "org.scalatest.FunSuiteLike", "org.scalatest.fixture.FunSuiteLike", "org.scalatest.funsuite.AnyFunSuiteLike", "org.scalatest.funsuite.FixtureAnyFunSuiteLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestFunSuiteMethods(methods, body)
    } else if (isInheritor(typeDef, "org.scalatest.PropSpecLike", "org.scalatest.fixture.PropSpecLike", "org.scalatest.propspec.AnyPropSpecLike", "org.scalatest.propspec.FixtureAnyPropSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestPropSpecMethods(methods, body)
    } else if (isInheritor(typeDef, "org.scalatest.WordSpecLike", "org.scalatest.fixture.WordSpecLike", "org.scalatest.wordspec.AnyWordSpecLike", "org.scalatest.wordspec.FixtureAnyWordSpecLike")) {
      generateScalaTestBeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addScalaTestWordSpecMethods(methods, body, className)
    } else if (isInheritor(typeDef, "org.specs2.specification.script.SpecificationLike")) {
      generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      generateSpecs2ScriptSpecificationMethods(methods, body, className, typeDef)
    } else if (isInheritor(typeDef, "org.specs2.SpecificationLike")) {
      generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      addSpecs2SpecificationMethods(methods, body, className)
    } else if (isInheritor(typeDef, "org.specs2.mutable.SpecificationLike")) {
      generateSpecs2BeforeAndAfter(generateBefore, generateAfter, typeDef, body)
      generateSpecs2MutableSpecificationMethods(methods, body, className)
    } else if (isInheritor(typeDef, "utest.framework.TestSuite")) {
      val file = typeDef.getContainingFile
      assert(file.isInstanceOf[ScalaFile])
      file.asInstanceOf[ScalaFile].addImportForPath("utest._")
      generateUTestMethods(methods, body)
    }
  }
}

object ScalaTestGenerator {

  private def withAnnotation(annotation: String, typeDef: ScTypeDefinition, body: ScTemplateBody)
                            (generateMethods: PsiElement => Unit)
                            (implicit elementScope: ElementScope): Unit =
    elementScope.getCachedClass(annotation) match {
      case Some(clazz: ScTypeDefinition) =>
        ExtractSuperUtil.addExtendsTo(typeDef, clazz)
        generateMethods(body.getLastChild)
      case _ =>
    }

  private def generateScalaTestBeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean,
                                              typeDef: ScTypeDefinition, body: ScTemplateBody): Unit = {
    import typeDef.{elementScope, projectContext}

    if (!generateBefore || generateAfter) return

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

  private def generateSpecs2BeforeAndAfter(generateBefore: Boolean, generateAfter: Boolean,
                                           typeDef: ScTypeDefinition, body: ScTemplateBody): Unit = {
    import typeDef.{elementScope, projectContext}

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

  private def addScalaTestFeatureSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val methodStrings = methods.map(m => s"scenario (${m.nameQuoted}){\n\n}\n")
      val methodsConcat = methodStrings.mkString("\n")
      val result = 
        s"""feature("Methods tests") {
           |$methodsConcat}""".stripMargin
      templateBody.addBefore(createExpressionFromText(result), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFlatSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      templateBody.addBefore(createExpressionFromText(s"behavior of ${className.quoted}"), closingBrace)
      templateBody.addBefore(createNewLine("\n\n"), closingBrace)
      val results = methods.map(m => s"it should ${m.nameQuoted} in {\n\n}")
      results.map(createExpressionFromText).foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(createNewLine("\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestFreeSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val methodStrings = methods.map(m => s"${m.nameQuoted} in {\n\n}\n")
      val methodsConcat = methodStrings.mkString("\n")
      val result = 
        s""""Methods tests" - {
           |$methodsConcat}""".stripMargin
      templateBody.addBefore(createExpressionFromText(result), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val methodStrings = methods.map(m => s"it(${("should " + m.name).quoted}) {\n\n}\n")
      val methodsConcat  = methodStrings.mkString("\n")
      val result = s"describe(${className.quoted}) {\n$methodsConcat}"
      templateBody.addBefore(createExpressionFromText(result), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addScalaTestFunSuiteMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      val results = methods.map(m => s"test(${("test" + m.name.capitalize).quoted}) {\n\n}")
      results.map(createExpressionFromText).foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(createNewLine("\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestPropSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val closingBrace = templateBody.getLastChild
      val results = methods.map(m => s"property(${(m.name + " property").quoted}){\n\n}")
      results.map(createExpressionFromText).foreach(expr => {
        templateBody.addBefore(expr, closingBrace)
        templateBody.addBefore(createNewLine("\n\n"), closingBrace)
      })
    }
  }

  private def addScalaTestWordSpecMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val methodsStrings = methods.map(m => s"${m.nameQuoted} in {\n\n}\n")
      val methodsConcat = methodsStrings.mkString("\n")
      val result = s"${className.quoted} should {\n$methodsConcat}"
      templateBody.addBefore(createExpressionFromText(result), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def addSpecs2SpecificationMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    val testNames = methods.map("test" + _.name.capitalize)
    val normalIndent = FormatterUtil.getNormalIndentString(projectContext)
    val doubleIndent = normalIndent + normalIndent

    val checkMethodsString =
      if (methods.nonEmpty) {
        val testNamesConcat = testNames.map(testName => doubleIndent + testName + " $" + testName).mkString("\n")
        s"\n${normalIndent}Methods of $className should pass tests:\n$testNamesConcat"
      }
      else ""
    val closingBrace = templateBody.getLastChild
    val qqq = MultilineStringUtil.MultilineQuotes
    templateBody.addBefore(createMethodFromText(s"def is = s2$qqq$checkMethodsString\n$normalIndent$qqq"), closingBrace)
    templateBody.addBefore(createNewLine(), closingBrace)
    testNames.map { testName =>
      templateBody.addBefore(createMethodFromText(s"def $testName = ok"), closingBrace)
      templateBody.addBefore(createNewLine(), closingBrace)
    }
  }

  private def generateSpecs2ScriptSpecificationMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String, typeDef: ScTypeDefinition): Unit = {
    import templateBody.{elementScope, projectContext}

    withAnnotation("org.specs2.specification.Groups", typeDef, templateBody) { closingBrace =>
      val testNames = methods.map("test" + _.name.capitalize)
      val normalIndent = FormatterUtil.getNormalIndentString(projectContext)
      val doubleIndent = normalIndent + normalIndent
      val checkMethodsString =
        if (methods.nonEmpty) {
          val testNamesConcat = testNames.map(doubleIndent + "+ " + _).mkString("\n")
          s"\n${normalIndent}Methods of $className should pass tests:\n$testNamesConcat"
        }
        else ""

      val qqq = MultilineStringUtil.MultilineQuotes
      templateBody.addBefore(createMethodFromText(s"def is = s2$qqq$checkMethodsString\n$doubleIndent$qqq"), closingBrace)
      templateBody.addBefore(createNewLine(), closingBrace)
      if (methods.nonEmpty) {
        val testNamesConcat = testNames.map("eg := ok //" + _).mkString("\n")
        val result = s"${className.quoted} - new group {\n$testNamesConcat\n}"
        templateBody.addBefore(createExpressionFromText(result), closingBrace)
        templateBody.addBefore(createNewLine(), closingBrace)
      }
    }
  }

  private def generateSpecs2MutableSpecificationMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody, className: String): Unit = {
    import templateBody.projectContext

    if (methods.nonEmpty) {
      val methodStrings = methods.map(m => s"${m.nameQuoted} in {\nok\n}\n")
      val methodsConcat = methodStrings.mkString("\n")
      val result = s"${className.quoted} should {\n$methodsConcat}"
      templateBody.addBefore(createExpressionFromText(result), templateBody.getLastChild)
      templateBody.addBefore(createNewLine(), templateBody.getLastChild)
    }
  }

  private def generateUTestMethods(methods: Seq[MemberInfo], templateBody: ScTemplateBody): Unit = {
    import templateBody.projectContext
    val normalIndent = FormatterUtil.getNormalIndentString(projectContext)
    templateBody.addBefore(createElement("val tests = TestSuite{}")(Def.parse(_)), templateBody.getLastChild)
    if (methods.nonEmpty) {
      val methodStrings = methods.map(m => s"$normalIndent${m.nameQuoted} - {}\n")
      val methodsConcat = methodStrings.mkString("\n")
      val result = s"val methodsTests = TestSuite{\n$methodsConcat}"
      templateBody.addBefore(createElement(result)(Def.parse(_)), templateBody.getLastChild)
    }
  }
  
  private implicit class StringOps(private val str: String) extends AnyVal {
    def quoted: String = "\"" + str + "\""
  }

  private implicit class MemberInfoOps(private val info: MemberInfo) extends AnyVal {
    def name: String = info.name
    def nameQuoted: String = info.name.quoted
  }
}
