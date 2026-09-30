package com.loveos.api.pairing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "couple_members")
@Getter
@Setter
@NoArgsConstructor
public class CoupleMember {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "couple_id", nullable = false)
  private UUID coupleId;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CoupleRole role;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CoupleMember member)) return false;
    return id != null && id.equals(member.id);
  }

  @Override
  public int hashCode() {
    return CoupleMember.class.hashCode();
  }
}