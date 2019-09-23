package org.jetbrains.plugins.scala.worksheet;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

final public class WorksheetLanguage extends Language implements DependentLanguage {

    public static final WorksheetLanguage INSTANCE = new WorksheetLanguage();

    private WorksheetLanguage() {
        super(ScalaLanguage.INSTANCE, "Scala Worksheet");
    }
}
