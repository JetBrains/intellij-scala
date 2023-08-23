package org.jetbrains.plugins.scala.project.template;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public enum Kind {

    Binaries("(?<!-src|-sources|-javadoc)"),
    Sources("-(src|sources)"),
    Docs("-javadoc");

    private String myRegex;

    Kind(String regex) {
        myRegex = regex;
    }

    public Predicate<String> getPattern(Artifact artifact) {
        return Pattern.compile(artifact.prefix() + ".*" + myRegex + "\\.jar").asPredicate();
    }
}
