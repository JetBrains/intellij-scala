package org.jetbrains.plugins.scala.editor.codeFolding;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.openapi.options.BeanConfigurable;
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle;
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings;

public class ScalaCodeFoldingOptionsProvider extends BeanConfigurable<ScalaCodeFoldingSettings> implements CodeFoldingOptionsProvider {

  public ScalaCodeFoldingOptionsProvider() {
    super(ScalaCodeFoldingSettings.getInstance(), "Scala");
    ScalaCodeFoldingSettings s = getInstance();

    checkBox(ScalaEditorBundle.message("checkbox.collapse.block.comments"), s::isCollapseBlockComments, s::setCollapseBlockComments);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.shell.comments"), s::isCollapseShellComments, s::setCollapseShellComments);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.method.call.bodies"), s::isCollapseMethodCallBodies, s::setCollapseMethodCallBodies);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.template.bodies"), s::isCollapseTemplateBodies, s::setCollapseTemplateBodies);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.other.definition.bodies"), s::isCollapseDefinitionWithAssignmentBodies, s::setCollapseDefinitionWithAssignmentBodies);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.type.lambdas"), s::isCollapseTypeLambdas, s::setCollapseTypeLambdas);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.packagings"), s::isCollapsePackagings, s::setCollapsePackagings);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.multiline.strings"), s::isCollapseMultilineStrings, s::setCollapseMultilineStrings);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.custom.regions"), s::isCollapseCustomRegions, s::setCollapseCustomRegions);
    checkBox(ScalaEditorBundle.message("checkbox.collapse.multiline.blocks"), s::isCollapseMultilineBlocks, s::setCollapseMultilineBlocks);
    checkBox(ScalaEditorBundle.message("checkbox.add.folding.for.all.blocks"), s::isFoldingForAllBlocks, s::setFoldingForAllBlocks);
  }
}
