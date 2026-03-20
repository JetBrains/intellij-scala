package org.jetbrains.plugins.scala.lang.actions.editor.enter_long_tests.scala3

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_AfterAssignOrArrowSign_Statements
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.afterAssignOrArrowSignStatementsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_AfterAssignOrArrowSign_Expressions
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.afterAssignOrArrowSignExpressionsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_ForEnumeratorsAll_Statements
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.forEnumeratorsAllStatementsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_ForEnumeratorsAll_Expressions
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.forEnumeratorsAllExpressionsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_ControlFlow_Statements
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.controlFlowStatementsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_ControlFlow_Expressions
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.controlFlowExpressionsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_Extensions
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.extensionsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_TemplateDefinitions
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.templateDefinitionsTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_GivenWith
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.givenWithTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CodeAfterCaret_AfterAssignOrArrowSign_1
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.codeAfterCaretAfterAssignOrArrowSign1Tests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CodeAfterCaret_AfterAssignOrArrowSign_2
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.codeAfterCaretAfterAssignOrArrowSign2Tests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CodeAfterCaret_AfterAssignOrArrowSign_3
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.codeAfterCaretAfterAssignOrArrowSign3Tests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CodeAfterCaret_ForEnumeratorsAll
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.codeAfterCaretForEnumeratorsAllTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CodeAfterCaret_ControlFlow
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.codeAfterCaretControlFlowTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CaseClauses_Braceless
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.caseClausesBracelessTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CaseClauses_WithBraces
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.caseClausesWithBracesTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_CaseClauses_TryCatch
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.caseClausesTryCatchTests()
}

final class Scala3BracelessSyntaxEnterHandlerTest_Exhaustive_NestedBlockInsertion
  extends Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveSingleGroupBase {

  override protected def createTestParameters(): Array[AnyRef] =
    Scala3BracelessSyntaxEnterHandlerTest_ExhaustiveGenerator.nestedBlockInsertionTests()
}
