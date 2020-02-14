package org.jetbrains.plugins.scala.editor.codeFolding;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.openapi.options.BeanConfigurable;
import org.jetbrains.plugins.scala.editor.EditorBundle;
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings;

public class ScalaCodeFoldingOptionsProvider extends BeanConfigurable<ScalaCodeFoldingSettings> implements CodeFoldingOptionsProvider {

  public ScalaCodeFoldingOptionsProvider() {
    super(ScalaCodeFoldingSettings.getInstance(), "Scala");
    ScalaCodeFoldingSettings s = getInstance();

    checkBox(EditorBundle.message("checkbox.collapse.block.comments"), s::isCollapseBlockComments, s::setCollapseBlockComments);
    checkBox(EditorBundle.message("checkbox.collapse.shell.comments"), s::isCollapseShellComments, s::setCollapseShellComments);
    checkBox(EditorBundle.message("checkbox.collapse.method.call.bodies"), s::isCollapseMethodCallBodies, s::setCollapseMethodCallBodies);
    checkBox(EditorBundle.message("checkbox.collapse.template.bodies"), s::isCollapseTemplateBodies, s::setCollapseTemplateBodies);
    checkBox(EditorBundle.message("checkbox.collapse.type.lambdas"), s::isCollapseTypeLambdas, s::setCollapseTypeLambdas);
    checkBox(EditorBundle.message("checkbox.collapse.packagings"), s::isCollapsePackagings, s::setCollapsePackagings);
    checkBox(EditorBundle.message("checkbox.collapse.multiline.strings"), s::isCollapseMultilineStrings, s::setCollapseMultilineStrings);
    checkBox(EditorBundle.message("checkbox.collapse.custom.regions"), s::isCollapseCustomRegions, s::setCollapseCustomRegions);
    checkBox(EditorBundle.message("checkbox.collapse.multiline.blocks"), s::isCollapseMultilineBlocks, s::setCollapseMultilineBlocks);
    checkBox(EditorBundle.message("checkbox.add.folding.for.all.blocks"), s::isFoldingForAllBlocks, s::setFoldingForAllBlocks);
  }
}
