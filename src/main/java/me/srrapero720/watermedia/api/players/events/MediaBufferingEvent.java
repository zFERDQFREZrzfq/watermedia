package me.srrapero720.watermedia.api.players.events;

import me.srrapero720.watermedia.api.players.AbstractPlayer;

public class MediaBufferingEvent extends Event {
    public static class Start extends MediaBufferingEvent {

    }

    public static class Progress extends MediaBufferingEvent {

        public Progress(AbstractPlayer player, float newCache) {

        }
    }

    public static class End extends MediaBufferingEvent {

        public End(AbstractPlayer player) {

        }
    }
}