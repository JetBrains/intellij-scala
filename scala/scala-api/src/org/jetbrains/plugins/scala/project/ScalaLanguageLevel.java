package org.jetbrains.plugins.scala.project;

public enum ScalaLanguageLevel implements scala.math.Ordered<ScalaLanguageLevel>, Named {

    Scala_2_9("2.9"),
    Scala_2_10("2.10"),
    Scala_2_11("2.11"),
    Scala_2_12("2.12"),
    Scala_2_13("2.13"),
    Scala_2_14("2.14");

    private final String myVersion;

    ScalaLanguageLevel(String version) {
        myVersion = version;
    }

    public String getVersion() {
        return myVersion;
    }

    @Override
    public String getName() {
        return getVersion();
    }

    @Override
    public int compare(ScalaLanguageLevel that) {
        return super.compareTo(that);
    }

    public static ScalaLanguageLevel getDefault() {
        return Scala_2_12;
    }
}
