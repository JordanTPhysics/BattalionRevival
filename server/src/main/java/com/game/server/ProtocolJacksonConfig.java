package com.game.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.network.protocol.ProtocolJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProtocolJacksonConfig {
    @Bean
    public ObjectMapper battalionProtocolMapper() {
        return ProtocolJson.mapper();
    }
}
