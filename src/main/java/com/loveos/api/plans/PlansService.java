package com.loveos.api.plans;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PlansService {
  private final CoupleAccess access;
  private final PlansRepository documents;
  private final ObjectMapper json;

  public PlansService(CoupleAccess access, PlansRepository documents, ObjectMapper json) {
    this.access = access;
    this.documents = documents;
    this.json = json;
  }

  @Transactional(readOnly = true)
  public ObjectNode get(UUID userId) {
    UUID coupleId = access.requireMembership(userId).coupleId();
    return documents.findById(coupleId).map(document -> view(document, userId)).orElse(null);
  }

  @Transactional
  public ObjectNode replace(UUID userId, ObjectNode input) {
    UUID coupleId = access.requireMembershipForUpdate(userId).coupleId();
    if (input == null || json.writeValueAsString(input).length() > 200_000) {
      throw invalid("Plans exceed the size limit");
    }
    PlansDocument document = documents.findById(coupleId).orElse(null);
    if (document != null && (!input.path("revision").isIntegralNumber()
      || input.path("revision").asLong(-1) != document.getRevision())) {
      throw invalid("Plans changed on another device. Reload before saving.");
    }
    ObjectNode next = input.deepCopy();
    next.retain("trip", "yearBoard", "promises", "lifetimeTarget", "capsules", "letters",
      "watchRoom", "sharingWindow", "minutesRemaining");
    ObjectNode previous = document == null ? json.createObjectNode() : parse(document);
    validateBoard(next.path("yearBoard"));
    mergeSealed(next, previous, "capsules", userId);
    mergeSealed(next, previous, "letters", userId);
    next.remove("revision");
    if (document == null) {
      document = new PlansDocument();
      document.setCoupleId(coupleId);
    }
    document.setPayload(json.writeValueAsString(next));
    return view(documents.saveAndFlush(document), userId);
  }

  private void validateBoard(JsonNode board) {
    if (!board.isObject() || !board.path("tiles").isArray() || board.path("tiles").size() > 25) {
      throw invalid("A Bingo board supports up to 25 cards");
    }
    Set<String> ids = new HashSet<>();
    for (JsonNode tile : board.path("tiles")) {
        if (!tile.isObject() || !tile.path("id").isString() || tile.path("id").asText().isBlank() || !ids.add(tile.path("id").asText())
          || !tile.path("title").isString() || tile.path("title").asText().isBlank() || tile.path("title").asText().length() > 140
          || (!tile.path("note").isMissingNode() && !tile.path("note").isNull() && !tile.path("note").isString())
          || tile.path("note").asText().length() > 1000
          || !Set.of("todo", "in-progress", "done").contains(tile.path("state").asText())) {
        throw invalid("Invalid Bingo card");
      }
    }
  }

  private void mergeSealed(ObjectNode next, ObjectNode previous, String field, UUID userId) {
    JsonNode supplied = next.path(field);
    if (!supplied.isArray() || supplied.size() > 200) throw invalid("Invalid " + field);
    Map<String, JsonNode> existing = new HashMap<>();
    previous.path(field).forEach(item -> existing.put(item.path("id").asText(), item));
    Set<String> ids = new HashSet<>();
    ArrayNode merged = json.createArrayNode();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (JsonNode item : supplied) {
      String id = item.path("id").asText();
      if (!item.isObject() || id.isBlank() || !ids.add(id)) throw invalid("Invalid sealed item");
      ObjectNode stored;
      if (existing.containsKey(id)) {
        stored = (ObjectNode) existing.get(id).deepCopy();
        boolean opening = field.equals("capsules") ? item.path("opened").asBoolean()
            : item.path("state").asText().equals("opened");
        if (opening && date(stored.path("opensAt").asText()).isAfter(today)) {
          throw invalid("This item is still sealed");
        }
        if (opening) {
          if (field.equals("capsules")) stored.put("opened", true);
          else stored.put("state", "opened");
        }
      } else {
        stored = (ObjectNode) item.deepCopy();
        LocalDate opensAt = date(stored.path("opensAt").asText());
        if (!stored.path("title").isString() || stored.path("title").asText().isBlank() || stored.path("title").asText().length() > 140) {
          throw invalid("A title is required");
        }
        stored.put("authorId", userId.toString());
        if (field.equals("capsules")) {
          if (!opensAt.isAfter(today)) throw invalid("Choose a future capsule date");
          JsonNode payload = stored.path("payload");
          if (!payload.isObject() || payload.isEmpty()) throw invalid("Add content to the capsule");
          ObjectNode counts = json.createObjectNode();
          for (String kind : payload.propertyNames()) {
            if (!Set.of("letter", "prediction", "promise", "question").contains(kind)
                || !payload.path(kind).isString() || payload.path(kind).asText().isBlank()
                || payload.path(kind).asText().length() > 5000) throw invalid("Invalid capsule content");
            counts.put(kind, 1);
          }
          stored.set("contents", counts);
          stored.put("createdAt", today.toString());
          stored.put("opened", false);
          stored.put("vaultCode", "TOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        } else {
          if (stored.path("body").asText().isBlank() || stored.path("body").asText().length() > 20_000) {
            throw invalid("A letter body is required");
          }
          stored.put("state", opensAt.isAfter(today) ? "sealed" : "ready");
          stored.put("writtenAt", today.toString());
          stored.put("teaser", opensAt.isAfter(today) ? "A letter waiting for you" : stored.path("teaser").asText());
        }
      }
      merged.add(stored);
    }
    for (String id : existing.keySet()) {
      if (!ids.contains(id)) merged.add(existing.get(id));
    }
    next.set(field, merged);
  }

  private ObjectNode view(PlansDocument document, UUID userId) {
    ObjectNode value = parse(document);
    value.put("revision", document.getRevision());
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (JsonNode item : value.path("capsules")) {
      ObjectNode capsule = (ObjectNode) item;
      capsule.put("sealedBy", capsule.path("authorId").asText().equals(userId.toString()) ? "me" : "partner");
      capsule.remove("authorId");
      if (!capsule.path("opened").asBoolean() || date(capsule.path("opensAt").asText()).isAfter(today)) {
        capsule.remove("payload");
      }
    }
    for (JsonNode item : value.path("letters")) {
      ObjectNode letter = (ObjectNode) item;
      boolean mine = letter.path("authorId").asText().equals(userId.toString());
      letter.put("from", mine ? "me" : "partner");
      letter.put("to", mine ? "partner" : "me");
      letter.remove("authorId");
      if (date(letter.path("opensAt").asText()).isAfter(today)) letter.putNull("body");
      else if (!letter.path("state").asText().equals("opened")) letter.put("state", "ready");
    }
    return value;
  }

  private ObjectNode parse(PlansDocument document) {
    return (ObjectNode) json.readTree(document.getPayload());
  }

  private static LocalDate date(String value) {
    try { return LocalDate.parse(value); }
    catch (RuntimeException invalid) { throw invalid("Invalid unlock date"); }
  }

  private static AppException invalid(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, message);
  }
}