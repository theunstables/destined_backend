package com.loveos.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The LoveOS API.
 *
 * <p>This is a port of the Node implementation in {@code ../ziyu_backend}, not a
 * redesign. The HTTP contract in {@code docs/API.md} is unchanged — same paths,
 * same envelope, same error codes — because the Expo client is already written
 * against it. If a response here differs from that document, this is the bug.
 *
 * <p>{@code @ConfigurationPropertiesScan} is enabled so settings arrive as typed,
 * validated records rather than scattered {@code @Value} lookups. A malformed
 * token lifetime should stop the application at startup, not produce a subtly
 * wrong expiry at three in the morning.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LoveOsApplication {

  public static void main(String[] args) {
    SpringApplication.run(LoveOsApplication.class, args);
  }
}
