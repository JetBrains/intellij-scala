package org.jetbrains.plugins.scala.settings;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
        name = "ScalaCodeFoldingSettings",
        storages = {
                @Storage("editor.xml"),
                @Storage(value = "scala_folding_settings.xml", deprecated = true)
        },
        reportStatistic = true
)
public class ScalaCodeFoldingSettings implements PersistentStateComponent<ScalaCodeFoldingSettings> {

  private boolean FOLD_ARGUMENT_BLOCK = false;
  private boolean FOLD_TEMPLATE_BODIES = false;
  private boolean FOLD_SHELL_COMMENTS = true;
  private boolean FOLD_BLOCK_COMMENTS = false;
  private boolean FOLD_PACKAGINGS = false;
  private boolean FOLD_TYPE_LAMBDA = false;
  private boolean FOLD_MULTILINE_STRING = false;
  private boolean FOLD_CUSTOM_REGION = false;
  private boolean FOLD_MULTILINE_BLOCKS = false;
  private boolean ADD_FOLDING_FOR_ALL_BLOCKS = false;

  public static ScalaCodeFoldingSettings getInstance() {
    return ServiceManager.getService(ScalaCodeFoldingSettings.class);
  }

  public ScalaCodeFoldingSettings getState() {
    return this;
  }

  public void loadState(@NotNull ScalaCodeFoldingSettings scalaCodeFoldingSettings) {
    XmlSerializerUtil.copyBean(scalaCodeFoldingSettings, this);
  }

  public boolean isCollapseFileHeaders() {
    return CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER;
  }

  public void setCollapseFileHeaders(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER = value;
  }

  public boolean isCollapseImports() {
    return  CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
  }

  public void setCollapseImports(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS = value;
  }

  public boolean isCollapseCustomRegions() {
    return FOLD_CUSTOM_REGION;
  }

  public void setCollapseCustomRegions(boolean value) {
    FOLD_CUSTOM_REGION = value;
  }

  public boolean isCollapseShellComments() {
    return FOLD_SHELL_COMMENTS;
  }

  public void setCollapseShellComments(boolean value) {
    FOLD_SHELL_COMMENTS = value;
  }

  public boolean isCollapseBlockComments() {
    return FOLD_BLOCK_COMMENTS;
  }

  public void setCollapseBlockComments(boolean value) {
    FOLD_BLOCK_COMMENTS = value;
  }

  public boolean isCollapseMethodCallBodies() {
    return FOLD_ARGUMENT_BLOCK;
  }

  public void setCollapseMethodCallBodies(boolean value) {
    FOLD_ARGUMENT_BLOCK = value;
  }

  public boolean isCollapseTemplateBodies() {
    return FOLD_TEMPLATE_BODIES;
  }

  public void setCollapseTemplateBodies(boolean value) {
    FOLD_TEMPLATE_BODIES = value;
  }

  public boolean isCollapseTypeLambdas() {
    return FOLD_TYPE_LAMBDA;
  }

  public void setCollapseTypeLambdas(boolean value) {
    FOLD_TYPE_LAMBDA = value;
  }

  public boolean isCollapsePackagings() {
    return FOLD_PACKAGINGS;
  }

  public void setCollapsePackagings(boolean value) {
    FOLD_PACKAGINGS = value;
  }

  public boolean isCollapseMultilineStrings() {
    return FOLD_MULTILINE_STRING;
  }

  public void setCollapseMultilineStrings(boolean value) {
    FOLD_MULTILINE_STRING = value;
  }

  public boolean isCollapseLineComments() {
    return JavaCodeFoldingSettings.getInstance().isCollapseEndOfLineComments();
  }

  public void setCollapseLineComments(boolean value) {
    JavaCodeFoldingSettings.getInstance().setCollapseEndOfLineComments(value);
  }

  public boolean isCollapseScalaDocComments() {
    return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
  }

  public void setCollapseScalaDocComments(boolean value) {
    CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS = value;
  }

  public boolean isCollapseMultilineBlocks() {
    return FOLD_MULTILINE_BLOCKS;
  }

  public void setCollapseMultilineBlocks(boolean value) {
    FOLD_MULTILINE_BLOCKS = value;
  }

  public boolean isFoldingForAllBlocks() {
    return ADD_FOLDING_FOR_ALL_BLOCKS;
  }

  public void setFoldingForAllBlocks(boolean value) {
    ADD_FOLDING_FOR_ALL_BLOCKS = value;
  }

  public boolean isCollapseI18nMessages() {
    return JavaCodeFoldingSettings.getInstance().isCollapseI18nMessages();
  }

  public void setCollapseI18nMessages(boolean value) {
    JavaCodeFoldingSettings.getInstance().setCollapseI18nMessages(value);
  }

}
