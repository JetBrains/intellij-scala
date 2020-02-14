package org.jetbrains.plugins.scala.editor.codeFolding;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.openapi.options.BeanConfigurable;
import org.jetbrains.plugins.scala.editor.EditorBundle;
import org.jetbrains.plugins.scala.settings.ScalaCodeFoldingSettings;

public class ScalaCodeFoldingOptionsProvider extends BeanConfigurable<ScalaCodeFoldingSettings> implements CodeFoldingOptionsProvider {

  public ScalaCodeFoldingOptionsProvider() {
    super(ScalaCodeFoldingSettings.getInstance(), "Scala");
    checkBox("FoldingForAllBlocks", EditorBundle.message("checkbox.add.folding.for.all.blocks"));

    checkBox("CollapseShellComments", EditorBundle.message("checkbox.collapse.shell.comments"));
    checkBox("CollapseBlockComments", EditorBundle.message("checkbox.collapse.block.comments"));
    checkBox("CollapseMethodCallBodies", EditorBundle.message("checkbox.collapse.method.call.bodies"));
    checkBox("CollapseTemplateBodies", EditorBundle.message("checkbox.collapse.template.bodies"));
    checkBox("CollapseTypeLambdas", EditorBundle.message("checkbox.collapse.type.lambdas"));
    checkBox("CollapsePackagings", EditorBundle.message("checkbox.collapse.packagings"));
    checkBox("CollapseMultilineStrings", EditorBundle.message("checkbox.collapse.multiline.strings"));
    checkBox("CollapseCustomRegions", EditorBundle.message("checkbox.collapse.custom.regions"));
    checkBox("CollapseMultilineBlocks", EditorBundle.message("checkbox.collapse.multiline.blocks"));
  }
}
