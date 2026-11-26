package com.myck.productivity.config;

import com.myck.productivity.telegram.client.TelegramProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração do bot do Telegram: carrega as propriedades app.telegram e liga o agendador do Spring,
 * usado pela verificação periódica de timers que acabaram.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TelegramProperties.class)
public class TelegramConfig {
}
