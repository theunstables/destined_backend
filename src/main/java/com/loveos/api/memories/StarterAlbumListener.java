package com.loveos.api.memories;

import com.loveos.api.memories.domain.Album;
import com.loveos.api.memories.repo.AlbumRepository;
import com.loveos.api.pairing.CoupleConnectedEvent;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class StarterAlbumListener {

  private static final List<Starter> STARTERS = List.of(
      new Starter("us", "Us", "💕"), new Starter("trips", "Trips", "✈️"),
      new Starter("dates", "Dates", "🥂"), new Starter("birthdays", "Birthdays", "🎂"),
      new Starter("littlethings", "Little Things", "✨"));

  private final AlbumRepository albums;

  public StarterAlbumListener(AlbumRepository albums) {
    this.albums = albums;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onConnected(CoupleConnectedEvent event) {
    for (Starter starter : STARTERS) {
      if (albums.existsByCoupleIdAndSystemKey(event.coupleId(), starter.key())) continue;
      Album album = new Album();
      album.setCoupleId(event.coupleId());
      album.setSystemKey(starter.key());
      album.setLabel(starter.label());
      album.setEmoji(starter.emoji());
      albums.save(album);
    }
  }

  private record Starter(String key, String label, String emoji) {}
}