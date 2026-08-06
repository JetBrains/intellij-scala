package org.jetbrains.plugins.scala.actions;

import javax.swing.*;

public final class GoToImplicitConversionAction {
    private static JList<Parameters> list = null;

    public static JList<Parameters> getList() {
        return list;
    }

    public static void setList(JList<Parameters> list) {
        GoToImplicitConversionAction.list = list;
    }
}