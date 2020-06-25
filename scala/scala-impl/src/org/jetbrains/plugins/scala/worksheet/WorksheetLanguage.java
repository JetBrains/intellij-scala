package org.jetbrains.plugins.scala.worksheet;

import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import org.jetbrains.plugins.scala.ScalaLanguage;

// TODO: eliminate this? We have two dimensions:
//  1) file type (ScalaFileType, WorksheetFileType)
//  2) language (ScalaLanguage, Scala3Language)
//  here we are bound to ScalaLanguage.INSTANCE. We would then wether have Worksheet3Language
//  OR (which is better) remove this file language completely, not to duplciate what is already saved in a file type
//  Yes scala code in a worksheet is not actually valid Scala code, but this is already expressed in it's file type.
//  Current hierarchy looks weird (in terms of base language, not direct inheritence):
//              ScalaLanguage
//               /        \
//  WorksheetLanguage   Scala3Language
//  This, for example leads to funny fact that:
//  for Scala 2 worksheets PsiFile.getLanguage returns WorksheetLanguage.INSTANCE
//  for Scala 3 worksheets PsiFile.getLanguage returns Scala3Language.INSTANCE
//  this is caused by org.jetbrains.plugins.scala.worksheet.ScalaWorksheetLanguageSubstitutor
//  which tries to workaround the fact that WorksheetLanguage is bound to ScalaLanguage.INSTANCE
final public class WorksheetLanguage extends Language implements DependentLanguage {

    public static final WorksheetLanguage INSTANCE = new WorksheetLanguage();

    private WorksheetLanguage() {
        super(ScalaLanguage.INSTANCE, "Scala Worksheet");
    }
}
