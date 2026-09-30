package com.loveos.api.memories;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.domain.*;
import com.loveos.api.memories.dto.MemoryRequests;
import com.loveos.api.memories.dto.MemoryResponses;
import com.loveos.api.memories.repo.*;
import com.loveos.api.pairing.CoupleAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoriesService implements MemoriesAccess {

  private static final List<StarterAlbum> STARTERS = List.of(
      new StarterAlbum("us", "Us", "💕"), new StarterAlbum("trips", "Trips", "✈️"),
      new StarterAlbum("dates", "Dates", "🥂"),
      new StarterAlbum("birthdays", "Birthdays", "🎂"),
      new StarterAlbum("littlethings", "Little Things", "✨"));

  private final CoupleAccess coupleAccess;
  private final MemoryRepository memories;
  private final MemoryPhotoRepository photos;
  private final MemoryPrivateNoteRepository privateNotes;
  private final TagRepository tags;
  private final MemoryTagRepository memoryTags;
  private final AlbumRepository albums;
  private final AlbumMemoryRepository albumMemories;
  private final UserRepository users;

  public MemoriesService(
      CoupleAccess coupleAccess, MemoryRepository memories, MemoryPhotoRepository photos,
      MemoryPrivateNoteRepository privateNotes, TagRepository tags,
      MemoryTagRepository memoryTags, AlbumRepository albums,
      AlbumMemoryRepository albumMemories, UserRepository users) {
    this.coupleAccess = coupleAccess;
    this.memories = memories;
    this.photos = photos;
    this.privateNotes = privateNotes;
    this.tags = tags;
    this.memoryTags = memoryTags;
    this.albums = albums;
    this.albumMemories = albumMemories;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public MemoryResponses.MemoryPage list(
      UUID userId, String cursor, int limit, String tag, String albumId,
      Boolean favorite, String onDay) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    List<Memory> filtered = memories.findByCoupleIdAndDeletedAtIsNullOrderByDateDescIdDesc(coupleId);
    String slug = optional(tag) == null ? null : optional(tag).toLowerCase(Locale.ROOT);
    UUID album = uuid(albumId, "Unknown album");
    Set<UUID> inAlbum = album == null ? Set.of() : albumMemories.findByAlbumId(album).stream()
        .map(AlbumMemory::getMemoryId).collect(Collectors.toSet());

    filtered = filtered.stream()
        .filter(memory -> favorite == null || memory.isFavorite() == favorite)
        .filter(memory -> album == null || inAlbum.contains(memory.getId()))
        .filter(memory -> slug == null || tagSlugs(memory.getId()).contains(slug))
        .filter(memory -> onDay == null || memory.getDate().toString().substring(5).equals(onDay))
        .toList();

    if (cursor != null) {
      UUID cursorId = uuid(cursor, "Unknown cursor");
      int position = -1;
      for (int index = 0; index < filtered.size(); index++) {
        if (filtered.get(index).getId().equals(cursorId)) position = index;
      }
      if (position < 0) throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown cursor");
      filtered = filtered.subList(position + 1, filtered.size());
    }

    boolean more = filtered.size() > limit;
    List<Memory> page = filtered.subList(0, Math.min(limit, filtered.size()));
    String next = more && !page.isEmpty() ? page.get(page.size() - 1).getId().toString() : null;
    return new MemoryResponses.MemoryPage(page.stream().map(memory -> dto(userId, memory)).toList(), next);
  }

  @Transactional(readOnly = true)
  public MemoryResponses.MemoryDto get(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    return dto(userId, active(coupleId, id));
  }

  @Transactional(readOnly = true)
  public List<MemoryResponses.MemoryDto> search(UUID userId, String text, int limit) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    String query = text.trim().toLowerCase(Locale.ROOT);
    return memories.findByCoupleIdAndDeletedAtIsNullOrderByDateDescIdDesc(coupleId).stream()
        .filter(memory -> contains(memory.getTitle(), query) || contains(memory.getCaption(), query)
            || contains(memory.getNote(), query) || contains(memory.getLocation(), query)
            || tagLabels(memory.getId()).stream().anyMatch(label -> contains(label, query)))
        .limit(limit).map(memory -> dto(userId, memory)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Summary summary(UUID userId, int recentLimit) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    List<Memory> active = memories.findByCoupleIdAndDeletedAtIsNullOrderByDateDescIdDesc(coupleId);
    long places = active.stream().map(Memory::getLocation).map(MemoriesService::optional)
        .filter(Objects::nonNull).map(value -> value.toLowerCase(Locale.ROOT)).distinct().count();
    Tag tripTag = tags.findByCoupleIdAndSlug(coupleId, "trips").orElse(null);
    long trips = tripTag == null ? 0 : memoryTags.findByMemoryIdIn(
        active.stream().map(Memory::getId).toList()).stream()
        .filter(link -> link.getTagId().equals(tripTag.getId())).count();
    List<RecentMemory> recent = active.stream().limit(recentLimit).map(memory -> {
      List<MemoryPhoto> ordered = photos.findByMemoryIdOrderByPositionAsc(memory.getId());
      return new RecentMemory(memory.getId().toString(), memory.getTitle(),
          memory.getDate().toString(), ordered.isEmpty() ? null : ordered.getFirst().getUrl(),
          memory.getLocation());
    }).toList();
    return new Summary(active.size(), places, trips, recent);
  }

  @Override
  @Transactional
  public UUID upsertDailyQuestionMemory(
      UUID userId, UUID memoryId, String date, String prompt, String note) {
    if (memoryId == null) {
      return UUID.fromString(create(userId, new MemoryRequests.CreateMemory(
          "Daily question", date, prompt, null, note, null, List.of(),
          List.of("Daily Questions"), false, List.of())).id());
    }
    update(userId, memoryId, new MemoryRequests.UpdateMemory(
        "Daily question", null, prompt, null, note, null, null, null, null));
    return memoryId;
  }

  @Transactional
  public MemoryResponses.MemoryDto create(UUID userId, MemoryRequests.CreateMemory input) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = new Memory();
    memory.setCoupleId(coupleId);
    memory.setAuthorId(userId);
    memory.setTitle(required(input.title()));
    memory.setDate(date(input.date()));
    memory.setCaption(optional(input.caption()));
    memory.setLocation(optional(input.location()));
    memory.setNote(optional(input.note()));
    memory.setFavorite(Boolean.TRUE.equals(input.favorite()));
    memories.saveAndFlush(memory);
    replacePhotos(memory.getId(), input.photoUri(), input.photos());
    replaceTags(coupleId, memory.getId(), input.tags() == null ? List.of() : input.tags());
    linkValidAlbums(coupleId, memory.getId(), input.albumIds());
    return dto(userId, memory);
  }

  @Transactional
  public MemoryResponses.MemoryDto update(
      UUID userId, UUID id, MemoryRequests.UpdateMemory input) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = active(coupleId, id);
    if (input.title() != null) memory.setTitle(required(input.title()));
    if (input.date() != null) memory.setDate(date(input.date()));
    if (input.caption() != null) memory.setCaption(optional(input.caption()));
    if (input.location() != null) memory.setLocation(optional(input.location()));
    if (input.note() != null) memory.setNote(optional(input.note()));
    if (input.favorite() != null) memory.setFavorite(input.favorite());
    if (input.photoUri() != null || input.photos() != null) {
      replacePhotos(id, input.photoUri(), input.photos());
    }
    if (input.tags() != null) replaceTags(coupleId, id, input.tags());
    return dto(userId, memories.saveAndFlush(memory));
  }

  @Transactional
  public MemoryResponses.MemoryDto upsertPrivateNote(UUID userId, UUID id, String note) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = memories.findActiveForUpdate(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Memory not found"));
    String value = required(note);
    MemoryPrivateNote privateNote = privateNotes.findByMemoryIdAndUserId(id, userId)
        .orElseGet(MemoryPrivateNote::new);
    privateNote.setMemoryId(id);
    privateNote.setUserId(userId);
    privateNote.setNote(value);
    privateNotes.saveAndFlush(privateNote);
    return dto(userId, memory);
  }

  @Transactional
  public MemoryResponses.MemoryDto withdrawPrivateNote(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = memories.findActiveForUpdate(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Memory not found"));
    privateNotes.deleteByMemoryIdAndUserId(id, userId);
    privateNotes.flush();
    return dto(userId, memory);
  }

  @Transactional
  public MemoryResponses.MemoryDto toggleFavorite(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = memories.findActiveForUpdate(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Memory not found"));
    memory.setFavorite(!memory.isFavorite());
    return dto(userId, memories.saveAndFlush(memory));
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Memory memory = active(coupleId, id);
    memory.setDeletedAt(Instant.now());
    memories.save(memory);
  }

  @Transactional
  public List<MemoryResponses.AlbumDto> listAlbums(UUID userId) {
    CoupleAccess.Membership membership = coupleAccess.requireMembership(userId);
    if (membership.connected()) ensureStarterAlbums(membership.coupleId());
    return albums.findByCoupleIdOrderByCreatedAtAsc(membership.coupleId()).stream()
        .map(this::albumDto).toList();
  }

  @Transactional
  public MemoryResponses.AlbumDto createAlbum(UUID userId, MemoryRequests.CreateAlbum input) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    String label = required(input.label());
    if (albums.existsByCoupleIdAndLabelIgnoreCase(coupleId, label)) {
      throw new AppException(ErrorCode.CONFLICT, "An album with that name already exists");
    }
    Album album = new Album();
    album.setCoupleId(coupleId);
    album.setLabel(label);
    album.setEmoji(optional(input.emoji()));
    album.setCoverUrl(input.coverUrl());
    try {
      return albumDto(albums.saveAndFlush(album));
    } catch (DataIntegrityViolationException exception) {
      throw new AppException(ErrorCode.CONFLICT, "An album with that name already exists");
    }
  }

  @Transactional(readOnly = true)
  public MemoryResponses.AlbumDto getAlbum(UUID userId, String id) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    return albumDto(album(coupleId, id));
  }

  @Transactional
  public void addToAlbum(UUID userId, UUID albumId, UUID memoryId) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    Album album = albums.findByIdAndCoupleId(albumId, coupleId)
        .orElseThrow(() -> AppException.notFound("Album or memory not found"));
    active(coupleId, memoryId);
    AlbumMemoryId key = new AlbumMemoryId(album.getId(), memoryId);
    if (!albumMemories.existsById(key)) {
      AlbumMemory link = new AlbumMemory();
      link.setAlbumId(albumId);
      link.setMemoryId(memoryId);
      link.setPosition((int) albumMemories.countByAlbumId(albumId));
      albumMemories.save(link);
    }
  }

  @Transactional
  public void removeFromAlbum(UUID userId, UUID albumId, UUID memoryId) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    albums.findByIdAndCoupleId(albumId, coupleId)
        .orElseThrow(() -> AppException.notFound("Album not found"));
    albumMemories.deleteByAlbumIdAndMemoryId(albumId, memoryId);
  }

  private void ensureStarterAlbums(UUID coupleId) {
    for (StarterAlbum starter : STARTERS) {
      if (albums.existsByCoupleIdAndSystemKey(coupleId, starter.key())) continue;
      Album album = new Album();
      album.setCoupleId(coupleId);
      album.setSystemKey(starter.key());
      album.setLabel(starter.label());
      album.setEmoji(starter.emoji());
      albums.save(album);
    }
    albums.flush();
  }

  private void replacePhotos(UUID memoryId, String cover, List<String> additional) {
    LinkedHashSet<String> urls = new LinkedHashSet<>();
    if (cover != null) urls.add(cover);
    if (additional != null) urls.addAll(additional);
    if (urls.size() > 20) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "A memory may have at most 20 photos");
    }
    photos.deleteByMemoryId(memoryId);
    photos.flush();
    int position = 0;
    for (String url : urls) {
      MemoryPhoto photo = new MemoryPhoto();
      photo.setMemoryId(memoryId);
      photo.setUrl(url);
      photo.setPosition(position++);
      photos.save(photo);
    }
  }

  private void replaceTags(UUID coupleId, UUID memoryId, List<String> labels) {
    memoryTags.deleteByMemoryId(memoryId);
    memoryTags.flush();
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    labels.forEach(label -> unique.putIfAbsent(
      label.trim().toLowerCase(Locale.ROOT), label.trim()));
    for (var entry : unique.entrySet()) {
      if (entry.getKey().isEmpty()) continue;
      Tag tag = tags.findByCoupleIdAndSlug(coupleId, entry.getKey()).orElseGet(() -> {
        Tag created = new Tag();
        created.setCoupleId(coupleId);
        created.setSlug(entry.getKey());
        created.setLabel(entry.getValue());
        return tags.saveAndFlush(created);
      });
      MemoryTag link = new MemoryTag();
      link.setMemoryId(memoryId);
      link.setTagId(tag.getId());
      memoryTags.save(link);
    }
  }

  private void linkValidAlbums(UUID coupleId, UUID memoryId, List<String> ids) {
    if (ids == null) return;
    int position = 0;
    for (String text : new LinkedHashSet<>(ids)) {
      UUID id = uuid(text, null);
      if (id == null || albums.findByIdAndCoupleId(id, coupleId).isEmpty()) continue;
      AlbumMemory link = new AlbumMemory();
      link.setAlbumId(id);
      link.setMemoryId(memoryId);
      link.setPosition(position++);
      albumMemories.save(link);
    }
  }

  private MemoryResponses.MemoryDto dto(UUID userId, Memory memory) {
    List<String> urls = photos.findByMemoryIdOrderByPositionAsc(memory.getId()).stream()
        .map(MemoryPhoto::getUrl).toList();
    User author = users.findById(memory.getAuthorId())
        .orElseThrow(() -> AppException.notFound("Memory author not found"));
    String authorName = optional(author.getNickname());
    if (authorName == null) authorName = optional(author.getDisplayName());
    if (authorName == null) authorName = author.getEmail().split("@", 2)[0];
    List<MemoryPrivateNote> noteRows = privateNotes.findByMemoryId(memory.getId());
    MemoryPrivateNote mine = noteRows.stream()
      .filter(item -> item.getUserId().equals(userId)).findFirst().orElse(null);
    MemoryPrivateNote partner = noteRows.stream()
      .filter(item -> !item.getUserId().equals(userId)).findFirst().orElse(null);
    boolean revealed = mine != null && partner != null;
    return new MemoryResponses.MemoryDto(
        memory.getId().toString(), memory.getTitle(), memory.getDate().toString(),
        memory.getCaption(), memory.getLocation(), urls.isEmpty() ? null : urls.getFirst(), urls,
        memory.getNote(), tagLabels(memory.getId()), memory.isFavorite(), authorName,
      memory.getCreatedAt().toString(), memory.getUpdatedAt().toString(),
      mine == null ? null : mine.getNote(), revealed ? partner.getNote() : null, revealed);
  }

  private List<String> tagLabels(UUID memoryId) {
    List<UUID> ids = memoryTags.findByMemoryId(memoryId).stream().map(MemoryTag::getTagId).toList();
    if (ids.isEmpty()) return List.of();
    Map<UUID, Tag> byId = tags.findByIdIn(ids).stream()
        .collect(Collectors.toMap(Tag::getId, Function.identity()));
    return ids.stream().map(byId::get).filter(Objects::nonNull).map(Tag::getLabel).toList();
  }

  private Set<String> tagSlugs(UUID memoryId) {
    List<UUID> ids = memoryTags.findByMemoryId(memoryId).stream().map(MemoryTag::getTagId).toList();
    if (ids.isEmpty()) return Set.of();
    return tags.findByIdIn(ids).stream().map(Tag::getSlug).collect(Collectors.toSet());
  }

  private MemoryResponses.AlbumDto albumDto(Album album) {
    List<AlbumMemory> links = albumMemories.findByAlbumId(album.getId()).stream()
        .sorted(Comparator.comparingInt(AlbumMemory::getPosition)).toList();
    String cover = album.getCoverUrl();
    if (cover == null && !links.isEmpty()) {
      Memory linked = memories.findByIdAndCoupleIdAndDeletedAtIsNull(
          links.getFirst().getMemoryId(), album.getCoupleId()).orElse(null);
      if (linked != null) {
        List<MemoryPhoto> covers = photos.findByMemoryIdOrderByPositionAsc(linked.getId());
        if (!covers.isEmpty()) cover = covers.getFirst().getUrl();
      }
    }
    return new MemoryResponses.AlbumDto(album.getId().toString(),
        album.getSystemKey() == null ? album.getId().toString() : album.getSystemKey(),
        album.getLabel(), album.getEmoji(), cover,
        links.stream().filter(link -> memories.findByIdAndCoupleIdAndDeletedAtIsNull(
            link.getMemoryId(), album.getCoupleId()).isPresent()).count());
  }

  private Album album(UUID coupleId, String id) {
    UUID uuid = uuid(id, null);
    return (uuid == null ? Optional.<Album>empty() : albums.findByIdAndCoupleId(uuid, coupleId))
        .or(() -> albums.findByCoupleIdAndSystemKey(coupleId, id))
        .orElseThrow(() -> AppException.notFound("Album not found"));
  }

  private Memory active(UUID coupleId, UUID id) {
    return memories.findByIdAndCoupleIdAndDeletedAtIsNull(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Memory not found"));
  }

  private static boolean contains(String value, String query) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(query);
  }

  private static LocalDate date(String value) {
    return DateUtils.parseCalendarDate(value);
  }

  private static UUID uuid(String value, String message) {
    if (value == null) return null;
    try { return UUID.fromString(value); }
    catch (IllegalArgumentException exception) {
      if (message == null) return null;
      throw new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
  }

  private static String required(String value) {
    String result = optional(value);
    if (result == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "Value is required");
    return result;
  }

  private static String optional(String value) {
    if (value == null) return null;
    String result = value.trim();
    return result.isEmpty() ? null : result;
  }

  private record StarterAlbum(String key, String label, String emoji) {}
}