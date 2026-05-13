package com.game.network.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Top-level WebSocket / REST payload discriminator for Jackson polymorphic deserialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CsMoveUnit.class, name = "CS_MOVE_UNIT"),
    @JsonSubTypes.Type(value = CsAttackUnit.class, name = "CS_ATTACK_UNIT"),
    @JsonSubTypes.Type(value = CsMoveAndAttackUnit.class, name = "CS_MOVE_AND_ATTACK_UNIT"),
    @JsonSubTypes.Type(value = CsFactoryBuild.class, name = "CS_FACTORY_BUILD"),
    @JsonSubTypes.Type(value = CsWarmachineBuild.class, name = "CS_WARMACHINE_BUILD"),
    @JsonSubTypes.Type(value = CsWarmachineDrill.class, name = "CS_WARMACHINE_DRILL"),
    @JsonSubTypes.Type(value = CsUnitRepair.class, name = "CS_UNIT_REPAIR"),
    @JsonSubTypes.Type(value = CsTransportPickup.class, name = "CS_TRANSPORT_PICKUP"),
    @JsonSubTypes.Type(value = CsTransportDisembark.class, name = "CS_TRANSPORT_DISEMBARK"),
    @JsonSubTypes.Type(value = CsConvertToAlbatross.class, name = "CS_CONVERT_TO_ALBATROSS"),
    @JsonSubTypes.Type(value = CsConvertToLeviathan.class, name = "CS_CONVERT_TO_LEVIATHAN"),
    @JsonSubTypes.Type(value = CsRevertTransport.class, name = "CS_REVERT_TRANSPORT"),
    @JsonSubTypes.Type(value = CsEndTurn.class, name = "CS_END_TURN"),
    @JsonSubTypes.Type(value = CsSurrender.class, name = "CS_SURRENDER"),
    @JsonSubTypes.Type(value = CsPing.class, name = "CS_PING"),
    @JsonSubTypes.Type(value = ScWelcome.class, name = "SC_WELCOME"),
    @JsonSubTypes.Type(value = ScSnapshot.class, name = "SC_SNAPSHOT"),
    @JsonSubTypes.Type(value = ScCommandResult.class, name = "SC_COMMAND_RESULT"),
    @JsonSubTypes.Type(value = ScError.class, name = "SC_ERROR"),
    @JsonSubTypes.Type(value = ScPong.class, name = "SC_PONG")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface NetEnvelope permits
    CsMoveUnit,
    CsAttackUnit,
    CsMoveAndAttackUnit,
    CsFactoryBuild,
    CsWarmachineBuild,
    CsWarmachineDrill,
    CsUnitRepair,
    CsTransportPickup,
    CsTransportDisembark,
    CsConvertToAlbatross,
    CsConvertToLeviathan,
    CsRevertTransport,
    CsEndTurn,
    CsSurrender,
    CsPing,
    ScWelcome,
    ScSnapshot,
    ScCommandResult,
    ScError,
    ScPong {

    int protocolVersion();
}
