package org.jetbrains.plugins.scala.lang.surroundWith;

import junit.framework.Test;
import junit.framework.TestCase;

public class SurroundWithTest extends TestCase {
    public static Test suite() {
        return new ScalaSurroundWithFileSetTestCase("/surroundWith/data/2/");
    }
}
