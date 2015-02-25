package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * User: Dmitry.Naydanov
 * Date: 06.02.15.
 */
public abstract class ServerWidgetEP {
  public static ExtensionPointName<ServerWidgetEP> EP_NAME = ExtensionPointName.create("org.intellij.scala.serverWidgetEP");

  public static ServerWidgetEP[] getAllWidgetEps() {
    return EP_NAME.getExtensions();
  }

  public abstract AnAction[] getAdditionalActions();
}
