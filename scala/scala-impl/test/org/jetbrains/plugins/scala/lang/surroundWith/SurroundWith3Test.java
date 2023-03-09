package org.jetbrains.plugins.scala.lang.surroundWith;

import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.plugins.scala.Scala3Language;

public class SurroundWith3Test extends TestCase {
    public static Test suite() {
        return new ScalaSurroundWithFileSetTestCase("/surroundWith/data/3/", Scala3Language.INSTANCE);
    }
}
