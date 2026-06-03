package org.jetbrains.jps.incremental.scala.remote;

/**
 * Contains the same constants as defined in com.facebook.nailgun.NGConstants. Should be kept in sync,
 * but in reality, the nailgun communication protocol will not be changed, and we also control the
 * sources and can publish it independently.
 * <p>
 * The constants are duplicated here since nailgun classes are not found on the classpath of the Scala plugin
 * (we only ship them in the lib/jps directory which is not on the classpath) and therefore we should not link
 * against the nailgun jar at compile time.
 */
final class NailgunConstants {
    private NailgunConstants() {}

    /** Chunk type marker for client exit chunks */
    public static final byte CHUNKTYPE_EXIT = 'X';

    /** Chunk type marker for stdout */
    public static final byte CHUNKTYPE_STDOUT = '1';

    /** Chunk type marker for stderr */
    public static final byte CHUNKTYPE_STDERR = '2';

    /** Chunk type marker for command line arguments */
    public static final byte CHUNKTYPE_ARGUMENT = 'A';

    /** Chunk type marker for client working directory */
    public static final byte CHUNKTYPE_WORKINGDIRECTORY = 'D';

    /** Chunk type marker for the command (alias or class) */
    public static final byte CHUNKTYPE_COMMAND = 'C';
}
