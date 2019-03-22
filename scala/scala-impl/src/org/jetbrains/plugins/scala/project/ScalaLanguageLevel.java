package org.jetbrains.plugins.scala.project;

public enum ScalaLanguageLevel implements scala.math.Ordered<ScalaLanguageLevel> {

    Snapshot("SNAPSHOT"),
    Scala_2_8("2.8"),
    Scala_2_9("2.9"),
    Scala_2_10("2.10"),
    Scala_2_11("2.11"),
    Scala_2_12("2.12"),
    Scala_2_13("2.13"),
    _2_14("2.14"),
    _3_0("3.0");

    private final String myVersion;

    ScalaLanguageLevel(String version) {
        myVersion = version;
    }

    public String getVersion() {
        return myVersion;
    }

    @Override
    public int compare(ScalaLanguageLevel that) {
        return super.compareTo(that);
    }

    public static ScalaLanguageLevel getDefault() {
        return Scala_2_12;
    }
}
