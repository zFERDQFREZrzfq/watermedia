package me.srrapero720.watermedia.vlc;

import java.nio.file.Files;
import java.nio.file.Path;

import me.srrapero720.watermedia.threads.ThreadUtil;
import me.lib720.caprica.vlcj4.factory.discovery.provider.DiscoveryDirectoryProvider;

import static me.srrapero720.watermedia.WaterMedia.LOGGER;

public record ProcessDiscoveryProvider(Path rootPath) implements DiscoveryDirectoryProvider {

    @Override
    public int priority() { return 5; }

    @Override
    public boolean supported() { return true; }

    @Override
    public String[] directories() {
        var vlc = rootPath.resolve("cache/vlc/");
        var config = vlc.resolve("version.cfg");
        var version = this.getLocalVersion(config);

        // Check if we need to update binaries
        if (version == null || !version.equals(VLCManager.version)) {
            this.clear();
            this.extract();
            this.setLocalVersion(config);
        } else {
            LOGGER.warn("VLC detected and match with the wrapped version");
        }

        return new String[]{vlc.toAbsolutePath().toString()};
    }

    private void clear() {
        LOGGER.warn("Running bin deletion from local files");
        for (var binary : BinManager.values()) binary.delete();
        LOGGER.warn("Bin deletion finished");

        LOGGER.warn("Running VLC LUAC script deletion from local files");
        for (var luac : LuaManager.values()) luac.delete();
        LOGGER.warn("VLC LUAC script deletion finished");
    }

    private void extract() {
        LOGGER.warn("Running bin extraction from JAR to local files");
        for (var binary : BinManager.values()) binary.extract();
        LOGGER.warn("Windows bin extraction finished");

        LOGGER.warn("Running VLC LUAC script extraction from JAR to local files");
        for (var luac : LuaManager.values()) luac.extract();
        LOGGER.warn("VLC LUAC script extraction finished");
    }

    private String getLocalVersion(Path from) {
        return ThreadUtil.tryAndReturn(defaultVar -> Files.exists(from) ? Files.readString(from) : defaultVar, null);
    }
    private void setLocalVersion(Path from) {
        ThreadUtil.trySimple(() -> Files.writeString(from, VLCManager.version), e -> LOGGER.error("Could not write to configuration file", e));
    }
}