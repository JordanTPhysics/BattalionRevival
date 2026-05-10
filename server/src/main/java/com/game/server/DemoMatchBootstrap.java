package com.game.server;

import com.game.engine.PlayableGameSession;
import com.game.model.map.GameMap;
import com.game.persistence.MapJsonPersistence;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.game.persistence.MapsWorkspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Configuration
public class DemoMatchBootstrap {

    @Bean
    CommandLineRunner bootstrapDemoMatch(MatchRoomRegistry registry) {
        return args -> {
            Path mapPath = MapsWorkspace.mapsDirectory().resolve("default.json");
            GameMap map;
            try {
                if (Files.isRegularFile(mapPath)) {
                    map = MapJsonPersistence.load(mapPath);
                } else {
                    map = DemoMaps.plains20();
                }
            } catch (Exception ex) {
                map = DemoMaps.plains20();
            }
            registry.getOrCreateRoom("demo", new PlayableGameSession(map), Set.of(1));
        };
    }
}
