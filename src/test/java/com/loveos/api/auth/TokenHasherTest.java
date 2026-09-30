package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

  private final TokenHasher hasher = new TokenHasher();

  @Test
  void generatedTokensAreUrlSafeUniqueAndContainRequestedEntropy() {
    var tokens = new HashSet<String>();
    IntStream.range(0, 100).forEach(ignored -> tokens.add(hasher.generate(32)));

    assertThat(tokens).hasSize(100);
    assertThat(tokens).allMatch(token -> token.matches("[A-Za-z0-9_-]{43}"));
  }

  @Test
  void hashIsDeterministicSha256Hex() {
    assertThat(hasher.hash("loveos"))
        .isEqualTo("217680d506a0c03bd12a05f38968e8c2ffefa7b541f388a53506d692a8464b1b")
        .hasSize(64);
  }
}
