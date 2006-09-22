package org.jetbrains.plugins.scala;

import com.intellij.lang.Language;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 15:01:34
 */
public class ScalaLanguage extends Language {
    protected ScalaLanguage(String s) {
        super(s);
    }

    protected ScalaLanguage(String s, String... strings) {
        super(s, strings);
    }

    public ScalaLanguage() {
        super("Scala");
    }
}
