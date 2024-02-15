package org.jetbrains.plugins.scala.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler.ReplaceTestOptions

object IntroduceVariableUtils {

  private val TestOptionsCommentPrefix = "//###"

  private val NameKey = "name"
  private val ReplaceAllKey = "replaceAll"
  private val ReplaceInCompanionObjectKey = "replaceInCompanionObject"
  private val ReplaceInInheritorsObjectKey = "replaceInInheritors"
  private val UseInplaceRefactoringKey = "inplace"

  def extractNameFromLeadingComment(fileText: String): (String, ReplaceTestOptions) = {
    val lineCommentIndex = fileText.indexOf(TestOptionsCommentPrefix)
    if (lineCommentIndex != 0)
      (fileText, ReplaceTestOptions())
    else {
      val newLineIndex = fileText.indexOf("\n")
      val testOptionsCommentContent = fileText.substring(TestOptionsCommentPrefix.length, newLineIndex)
      val contentAfterComment = fileText.substring(newLineIndex + 1)

      val testOptions = parseTestOptions(testOptionsCommentContent)
      (contentAfterComment, testOptions)
    }
  }

  //comment examples:
  // //### name=MyName, kind = all
  private def parseTestOptions(commentText: String): ReplaceTestOptions = {
    val content = commentText.stripPrefix(TestOptionsCommentPrefix).trim
    val attributes = parseAttributes(content)
    ReplaceTestOptions(
      definitionName = attributes.get(NameKey),
      replaceAllOccurrences = attributes.get(ReplaceAllKey).map(_.toBoolean),
      replaceOccurrencesInCompanionObjects = attributes.get(ReplaceInCompanionObjectKey).map(_.toBoolean),
      replaceOccurrencesInInheritors = attributes.get(ReplaceInInheritorsObjectKey).map(_.toBoolean),
      useInplaceRefactoring = attributes.get(UseInplaceRefactoringKey).map(_.toBoolean),
    )
  }

  private def parseAttributes(content: String): Map[String, String] = {
    val parts = content.split(",").map(_.trim)
    parts.map(parseTestOption).toMap
  }

  private def parseTestOption(attributeText: String): (String, String) =
    attributeText.split("=") match {
      case Array(key, value) => (key.trim, value.trim)
    }
}
