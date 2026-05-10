package com.game.network.protocol;

/**
 * Wire format and snapshot schema versioning for reconnect and compatibility checks.
 */
public final class ProtocolVersions {
    /** Bump when JSON envelope or command shapes change incompatibly. */
    public static final int NETWORK_PROTOCOL_VERSION = 1;
    /** Bump when {@link MatchSnapshot} tile/unit/player layout changes incompatibly. */
    public static final int MATCH_SNAPSHOT_SCHEMA_VERSION = 2;

    private ProtocolVersions() {
    }
}
