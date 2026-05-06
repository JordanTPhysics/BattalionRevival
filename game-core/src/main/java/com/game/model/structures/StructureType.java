package com.game.model.structures;

public enum StructureType {
    AirControl(20),
    Capital(30),
    SeaControl(20),
    GroundControl(20),
    OilAdvanced(20),
    OilRefinery(10),
    OilRig(20),
    Factory(10);

    private final int cover;

    StructureType(int cover) {
        this.cover = cover;
    }

    public int getCover() {
        return cover;
    }
}
