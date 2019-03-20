package org.jetbrains.plugins.scala.worksheet;

import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

public class WorksheetLanguage extends Language {

    public static final WorksheetLanguage INSTANCE = new WorksheetLanguage();

    private WorksheetLanguage() {
        super(ScalaLanguage.INSTANCE, "Scala Worksheet");
    }
}
