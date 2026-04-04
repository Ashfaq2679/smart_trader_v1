package com.techcobber.smarttrader.v1.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled-task execution.
 *
 * <p>With this configuration active, any bean method annotated with
 * {@link org.springframework.scheduling.annotation.Scheduled @Scheduled}
 * will be executed according to its declared schedule.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
