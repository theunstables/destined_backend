package com.loveos.api.plans;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlansService {
  private static final int MAX_BYTES = 512 * 1024;
  private static final Set<String> REQUIRED_KEYS = Set.of(
      "trip", "yearBoard", "promises", "lifetimeTarget", "capsules", "letters",
      "watchRoom", "sharingWindow", "minutesRemaining");
  private static final Set<String> PARTNER_FIELDS = Set.of("from", "to", "sealedBy", "pickedBy", "who");

  private final CouplePlanStateRepository repository;
  private final CoupleAccess couples;
  private final ObjectMapper mapper;

  public PlansService(
      CouplePlanStateRepository repository, CoupleAccess couples, ObjectMapper mapper) {
    this.repository = repository;
    this.couples = couples;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public JsonNode get(UUID userId) {
    CoupleAccess.Membership membership = couples.requireMembership(userId);
    return repository.findById(membership.coupleId())
        .map(row -> relative(parse(row.getJson()), identities(userId)))
        .orElse(null);
  }

  @Transactional
  public JsonNode replace(UUID userId, JsonNode state) {
    CoupleAccess.Membership membership = couples.requireMembershipForUpdate(userId);
    validate(state);
    Identities identities = identities(userId);
    JsonNode canonical = canonical(state.deepCopy(), identities);
    CouplePlanState row = repository.findById(membership.coupleId()).orElseGet(CouplePlanState::new);
    if (row.getJson() != null) {
      JsonNode stored = parse(row.getJson());
      preventEarlyCapsuleOpening(stored, canonical);
      preserveSealedLetterBodies(stored, canonical);
    } else {
      preventEarlyCapsuleOpening(null, canonical);
    }
    row.setCoupleId(membership.coupleId());
    row.setVersion(row.getVersion() + 1);
    row.setUpdatedBy(userId);
    row.setJson(write(canonical));
    repository.saveAndFlush(row);
    return relative(canonical.deepCopy(), identities);
  }

  private Identities identities(UUID userId) {
    List<CoupleAccess.Member> members = couples.members(userId);
    UUID partnerId = members.stream().map(CoupleAccess.Member::userId)
        .filter(id -> !id.equals(userId)).findFirst().orElse(null);
    return new Identities(userId, partnerId);
  }

  private void validate(JsonNode state) {
    if (state == null || !state.isObject()) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Plans state must be an object");
    }
    for (String key : REQUIRED_KEYS) {
      if (!state.has(key)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR, "Plans state is missing " + key);
      }
    }
    int size = write(state).getBytes(StandardCharsets.UTF_8).length;
    if (size > MAX_BYTES) {
      throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE, "Plans state exceeds 512 KB");
    }
  }

  private JsonNode canonical(JsonNode node, Identities identities) {
    transformPartnerIds(node, identities, true);
    return node;
  }

  private JsonNode relative(JsonNode node, Identities identities) {
    transformPartnerIds(node, identities, false);
    releaseDueLetters(node);
    maskSealedLetters(node);
    return node;
  }

  private void transformPartnerIds(JsonNode node, Identities identities, boolean toCanonical) {
    if (node instanceof ObjectNode object) {
      for (String field : List.copyOf(object.propertyNames())) {
        JsonNode value = object.get(field);
        if (PARTNER_FIELDS.contains(field) && value != null && value.isTextual()) {
          String mapped = toCanonical
              ? canonicalId(value.asText(), identities)
              : relativeId(value.asText(), identities);
          if (mapped != null) object.put(field, mapped);
        } else {
          transformPartnerIds(value, identities, toCanonical);
        }
      }
    } else if (node instanceof ArrayNode array) {
      array.forEach(item -> transformPartnerIds(item, identities, toCanonical));
    }
  }

  private static String canonicalId(String value, Identities identities) {
    if (value.equals("me")) return identities.me().toString();
    if (value.equals("partner") && identities.partner() != null) return identities.partner().toString();
    return value.equals("both") ? value : null;
  }

  private static String relativeId(String value, Identities identities) {
    if (value.equals(identities.me().toString())) return "me";
    if (identities.partner() != null && value.equals(identities.partner().toString())) return "partner";
    return value.equals("both") ? value : null;
  }

  private static void maskSealedLetters(JsonNode state) {
    JsonNode letters = state.get("letters");
    if (!(letters instanceof ArrayNode array)) return;
    for (JsonNode item : array) {
      if (item instanceof ObjectNode letter && "sealed".equals(letter.path("state").asText())) {
        letter.putNull("body");
      }
    }
  }

  private static void releaseDueLetters(JsonNode state) {
    JsonNode letters = state.get("letters");
    if (!(letters instanceof ArrayNode array)) return;
    LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode letter) || !"sealed".equals(letter.path("state").asText())) continue;
      String opensAt = letter.path("opensAt").asText("");
      try {
        if (!LocalDate.parse(opensAt).isAfter(today)) letter.put("state", "ready");
      } catch (java.time.format.DateTimeParseException ignored) {
        // Request validation owns malformed user input; an older stored row
        // should remain sealed rather than making the whole workspace unreadable.
      }
    }
  }

  private static void preserveSealedLetterBodies(JsonNode stored, JsonNode incoming) {
    JsonNode oldLetters = stored.get("letters");
    JsonNode newLetters = incoming.get("letters");
    if (!(oldLetters instanceof ArrayNode oldArray) || !(newLetters instanceof ArrayNode newArray)) return;
    HashMap<String, JsonNode> bodies = new HashMap<>();
    for (JsonNode item : oldArray) {
      if (item.hasNonNull("id") && item.hasNonNull("body")) {
        bodies.put(item.get("id").asText(), item.get("body"));
      }
    }
    for (JsonNode item : newArray) {
      if (!(item instanceof ObjectNode letter) || !letter.hasNonNull("id")) continue;
      if (letter.path("body").isNull() && bodies.containsKey(letter.get("id").asText())) {
        letter.set("body", bodies.get(letter.get("id").asText()));
      }
    }
  }

  private static void preventEarlyCapsuleOpening(JsonNode stored, JsonNode incoming) {
    JsonNode newCapsules = incoming.get("capsules");
    if (!(newCapsules instanceof ArrayNode newArray)) return;

    HashMap<String, Boolean> previouslyOpened = new HashMap<>();
    if (stored != null && stored.get("capsules") instanceof ArrayNode oldArray) {
      for (JsonNode item : oldArray) {
        if (item.hasNonNull("id")) {
          previouslyOpened.put(item.get("id").asText(), item.path("opened").asBoolean(false));
        }
      }
    }

    LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
    for (JsonNode item : newArray) {
      if (!item.path("opened").asBoolean(false) ||
          previouslyOpened.getOrDefault(item.path("id").asText(), false)) continue;
      try {
        if (LocalDate.parse(item.path("opensAt").asText()).isAfter(today)) {
          throw new AppException(ErrorCode.VALIDATION_ERROR, "Capsule cannot be opened yet");
        }
      } catch (java.time.format.DateTimeParseException exception) {
        throw new AppException(ErrorCode.VALIDATION_ERROR, "Capsule open date is invalid");
      }
    }
  }

  private JsonNode parse(String json) {
    try {
      return mapper.readTree(json);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored plans state is invalid JSON", exception);
    }
  }

  private String write(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Plans state cannot be serialized", exception);
    }
  }

  private record Identities(UUID me, UUID partner) {}
}
