package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, IntentionsUI}
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInspection.QuickFix
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.codeInspection.methodSignature.UnitMethodInspection
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.TypeAnnotationInspection
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * For the motivation behind this test, see
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.quickFixPriority]]
 *
 * Also keep in mind that since SCL-20508, unused declarations are not eligible for tightening access.
 */
class MakePrivateQuickFixIsAboveAddTypeAnnotationQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  //noinspection NotImplementedCode
  override protected lazy val description: String = ??? //not used

  private val AccessCanBePrivate = ScalaInspectionBundle.message("access.can.be.private")
  private val TypeAnnotationRequiredForUnit = ScalaInspectionBundle.message("type.annotation.required.for", "Unit definition")
  private val MethodWithUnitResultTypeIsParameterless = ScalaInspectionBundle.message("method.signature.unit.parameterless")

  private val MakePrivateQuickFix = ScalaInspectionBundle.message("make.private")
  private val AddTypeAnnotationQuickFix = ScalaInspectionBundle.message("add.type.annotation")
  private val ModifyCodeStyleQuickFix = ScalaInspectionBundle.message("quickfix.modify.code.style")
  private val AddEmptyParenthesesStyleQuickFix = ScalaInspectionBundle.message("empty.parentheses")

  override protected def descriptionMatches(s: String): Boolean = true

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
    myFixture.enableInspections(classOf[TypeAnnotationInspection])
    //I enable extra inspection to show the difference in order of fixes more clearly
    myFixture.enableInspections(classOf[UnitMethodInspection.Parameterless])
  }

  private def getOrderedQuickFixesAvailableInEditor: Seq[IntentionActionWithTextCaching] = {
    //This is a true way of getting actions (intention actions, quick fixes) in the order they will be shown to the user
    val cachedIntentions = IntentionsUI.getInstance(getProject).getCachedIntentions(getEditor, getFile)
    val allActions = cachedIntentions.getAllActions.asScala.toSeq
    allActions.filter { action =>
      val maybeQuickFix = unwrapQuickFix(action)
      maybeQuickFix.isDefined
    }
  }

  private def unwrapQuickFix(action: IntentionActionWithTextCaching): Option[QuickFix[_]] = {
    val unwrapped1 = IntentionActionDelegate.unwrap(action.getDelegate)
    val unwrapped2 = QuickFixWrapper.unwrap(unwrapped1)

    Option(unwrapped2).orElse(
      unwrapped1 match {
        case quickFix: QuickFix[_] => Some(quickFix)
        case _ => None
      }
    )
  }

  //noinspection SameParameterValue
  private def configureByTextAndGetHighlightsFor(fileText: String, highlightedElementText: String): Seq[HighlightInfo] = {
    configureByText(fileText)
    val highlightsAllInFile = findMatchingHighlightings(fileText)
    highlightsAllInFile.filter(_.getText == highlightedElementText).filter(_.getDescription != null)
  }

  //noinspection SameParameterValue
  private def doTest(
    fileText: String,
    definitionName: String,
    expectedWarningMessages: Seq[String],
    expectedOrderedActionTexts: Seq[String]
  ): Unit = {
    val highlights = configureByTextAndGetHighlightsFor(fileText, definitionName)
    assertCollectionEquals(
      "Warning messages do not match",
      expectedWarningMessages.sorted,
      highlights.map(_.getDescription).sorted
    )

    val actualActions = getOrderedQuickFixesAvailableInEditor
    assertCollectionEquals(
      s"Quick fixes at caret position ${getEditor.getCaretModel.getOffset} do not match (in the exact order)",
      expectedOrderedActionTexts,
      actualActions.map(_.getText)
    )
  }

  def test_when_add_type_annotation_quickfix_is_offered_show_prioritise_make_private_fix(): Unit = {
    //NOTE: we are using `get` prefix to trigger "Type annotation required"
    val fileText = s"private class A { def ${CARET}getMyDefinition = { 2 + 2; () }; getMyDefinition }"

    doTest(
      fileText,
      "getMyDefinition",
      expectedWarningMessages = Seq(
        AccessCanBePrivate,
        TypeAnnotationRequiredForUnit,
        MethodWithUnitResultTypeIsParameterless
      ),
      //NOTE: we test all quick fixes to explicitly show that the order is not alphabetical
      expectedOrderedActionTexts = Seq(
        MakePrivateQuickFix,
        AddEmptyParenthesesStyleQuickFix,
        AddTypeAnnotationQuickFix,
        ModifyCodeStyleQuickFix
      )
    )
  }

  def test_when_add_type_annotation_quickfix_should_be_offered_but_inspection_is_disabled_show_prioritise_make_private_fix(): Unit = {
    //NOTE: we are using `get` prefix to trigger "Type annotation required"
    val fileText = s"private class A { def ${CARET}getMyDefinition = { 2 + 2; () }; getMyDefinition }"

    val key = TypeAnnotationInspection.highlightKey
    val inspectionProfile = InspectionProjectProfileManager.getInstance(getProject).getCurrentProfile
    val isEnabledBefore = inspectionProfile.isToolEnabled(key)
    try {
      inspectionProfile.setToolEnabled(key.toString, false)

      doTest(
        fileText,
        "getMyDefinition",
        expectedWarningMessages = Seq(
          AccessCanBePrivate,
          MethodWithUnitResultTypeIsParameterless
        ),
        //NOTE: we test all quick fixes to explicitly show that the order is not alphabetical
        expectedOrderedActionTexts = Seq(
          AddEmptyParenthesesStyleQuickFix,
          MakePrivateQuickFix,
        )
      )
    } finally {
      inspectionProfile.setToolEnabled(key.toString, isEnabledBefore)
    }
  }

  def test_text_when_add_type_annotation_quickfix_is_not_offered_not_prioritise_make_private_fix(): Unit = {
    //NOTE: we are using `get` prefix to trigger "Type annotation required"
    //NOTE: the definition has explicit Unit type
    val fileText = s"""private class A { def ${CARET}getMyDefinition: Unit = { 2 + 2; () }; getMyDefinition }"""

    doTest(
      fileText,
      "getMyDefinition",
      expectedWarningMessages = Seq(
        AccessCanBePrivate,
        MethodWithUnitResultTypeIsParameterless
      ),
      //NOTE: we test all quick fixes to explicitly show that the order is not alphabetical
      expectedOrderedActionTexts = Seq(
        AddEmptyParenthesesStyleQuickFix,
        MakePrivateQuickFix,
      )
    )
  }
}
