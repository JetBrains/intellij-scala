package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import scala.Option;

/**
 * @author Pavel Fatin
 */
public abstract class ScalaFileFactory {
  public static ExtensionPointName<ScalaFileFactory> EP_NAME = ExtensionPointName.create("org.intellij.scala.scalaFileFactory");

  @Nullable
  public abstract Option<ScalaFile> createFile(FileViewProvider provider);
}
