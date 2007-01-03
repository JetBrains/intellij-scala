package org.jetbrains.plugins.scala;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 16:25:12
 */
public class ScalaFileType extends LanguageFileType {

  public static final ScalaFileType SCALA_FILE_TYPE = new ScalaFileType();
  public static final Icon SCALA_LOGO = IconLoader.getIcon("/org/jetbrains/plugins/scala/images/scala_logo.png");

  private ScalaFileType() {
    super(new ScalaLanguage());
  }

  @NotNull
  @NonNls
  public String getName() {
    return "Scala";
  }

  @NotNull
  public String getDescription() {
    return "Scala files";
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "scala";
  }

  public Icon getIcon() {
    return SCALA_LOGO;
  }
}
