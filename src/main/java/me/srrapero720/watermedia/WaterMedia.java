package me.srrapero720.watermedia;

import me.srrapero720.watermedia.api.WaterMediaAPI;
import me.srrapero720.watermedia.core.*;
import me.srrapero720.watermedia.core.exceptions.IllegalReloadException;
import me.srrapero720.watermedia.util.ThreadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class WaterMedia {
	public static final String ID = "watermedia";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);
	public static final Marker IT = MarkerFactory.getMarker("Bootstrap");

	static {
		LOGGER.warn(IT, "Class was loaded");
	}

	// RETAINERS
	private static WaterMedia instance;
	private static volatile Exception exception;
	private final IMediaLoader loader;

	public static WaterMedia getInstance(IMediaLoader loader) {
		if (instance == null && loader == null) throw new IllegalArgumentException("IMediaLoader must not be null with non instances");
		if (instance == null) return instance = new WaterMedia(loader);
		return instance;
	}

	public WaterMedia(IMediaLoader loader) {
		this.loader = loader;
		LOGGER.info(IT, "Running WATERMeDIA on {}", this.loader.getName());

        if (loader instanceof IEnvLoader) onEnvironmentInit((IEnvLoader) loader);
        else LOGGER.warn(IT, "Preloading mode enabled, must be loaded environment manually");
    }

	public void onEnvironmentInit(IEnvLoader loader) {
		// ENSURE WATERMeDIA IS NOT RUNNING ON SERVERS (except FABRIC)
		if (!loader.client() && !loader.development()) {
			exception = new IllegalStateException("WATERMeDIA is running on SERVER");

			LOGGER.error(IT, "###########################  ILLEGAL ENVIRONMENT  ###################################");
			LOGGER.error(IT, "WATERMeDIA is not designed to run on SERVERS. remove this mod from server to stop crashes");
			LOGGER.error(IT, "If dependant mods throws error loading WATERMeDIA classes report it to the creator");
			LOGGER.error(IT, "###########################  ILLEGAL ENVIRONMENT  ###################################");
		}

		// ENSURE FANCYVIDEO_API IS NOT INSTALLED (to prevent more bugreports about it)
		if (loader.installed("fancyvideo_api"))
			exception = new IllegalStateException("FancyVideo-API is explicit incompatible with WATERMeDIA, please remove it");

		// ENSURE IS NOT RUNNING BY TLAUNCHER
		if (loader.tlauncher())
			exception = new IllegalStateException("[CRITICAL] TLauncher is a virus launcher and not supported by WATERMeDIA - Suggested: SKLauncher, MultiMC");
	}

	public void init() {
		LOGGER.info(IT, "Starting WaterMedia");

		// RESOURCE EXTRACTOR
		LOGGER.info(IT, "Loading {}", ResourceManager.class.getSimpleName());
		ThreadUtil.trySimple(() -> ResourceManager.init(this.loader), e -> onLoadFailed(ResourceManager.class.getSimpleName(), e));

		// PREPARE API
		LOGGER.info(IT, "Loading {}", WaterMediaAPI.class.getSimpleName());
		ThreadUtil.trySimple(() -> WaterMediaAPI.init(this.loader), e -> onLoadFailed(WaterMediaAPI.class.getSimpleName(), e));

		// PREPARE STORAGES
		LOGGER.info(IT, "Loading {}", MediaStorage.class.getSimpleName());
		ThreadUtil.trySimple(() -> MediaStorage.init(this.loader), e -> onLoadFailed(MediaStorage.class.getSimpleName(), e));

		// PREPARE VLC
		LOGGER.info(IT, "Loading {}", VideoLAN.class.getSimpleName());
		ThreadUtil.trySimple(() -> VideoLAN.init(this.loader), e -> onLoadFailed(VideoLAN.class.getSimpleName(), e));

		// PREPARE LAVAPLAYER
		LOGGER.info(IT, "Loading {}", LavaPlayer.class.getSimpleName());
		ThreadUtil.trySimple(() -> LavaPlayer.init(this.loader), e -> onLoadFailed(LavaPlayer.class.getSimpleName(), e));

		LOGGER.info(IT, "Finished WaterMedia startup");
	}

	public void crash() { if (exception != null) throw new RuntimeException(exception); }
	private void onLoadFailed(String module, Exception e) {
		LOGGER.error(IT, "Exception loading {}", module, e);
		if (exception != null && !(e instanceof IllegalReloadException)) exception = e;
	}
}