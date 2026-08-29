package pp.sheero.permapiola;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import pp.sheero.permapiola.chat.ChatListener;
import pp.sheero.permapiola.chat.ChatManager;
import pp.sheero.permapiola.chat.EmoteGUIListener;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.chat.commands.*;
import pp.sheero.permapiola.commands.*;
import pp.sheero.permapiola.core.DayManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.core.PlayerConnectionListener;
import pp.sheero.permapiola.core.ServerPingListener;
import pp.sheero.permapiola.dementialwheel.DementialWheelCommand;
import pp.sheero.permapiola.dementialwheel.DementialWheelListener;
import pp.sheero.permapiola.dementialwheel.DementialWheelManager;
import pp.sheero.permapiola.dementialwheel.DementialWheelTask;
import pp.sheero.permapiola.discord.DiscordManager;
import pp.sheero.permapiola.hurricane.*;
import pp.sheero.permapiola.inactivity.AFKManager;
import pp.sheero.permapiola.inactivity.InactivityManager;
import pp.sheero.permapiola.inventory.*;
import pp.sheero.permapiola.moderation.CombatLogListener;
import pp.sheero.permapiola.moderation.CommandBlockerListener;
import pp.sheero.permapiola.moderation.MiningListener;
import pp.sheero.permapiola.playtime.*;
import pp.sheero.permapiola.scoreboard.ScoreboardManager;
import pp.sheero.permapiola.scoreboard.SidebarCommand;
import pp.sheero.permapiola.teams.*;
import pp.sheero.permapiola.totem.*;
import pp.sheero.permapiola.utils.LuckPermsUtils;

public final class PermaPiola extends JavaPlugin {

    private static PermaPiola instance;

    // ==========================================================
    // MANAGERS
    // ==========================================================
    private LanguageManager languageManager;
    private EmoteManager emoteManager;
    private ChatManager chatManager;
    private ScoreboardManager scoreboardManager;
    private AFKManager afkManager;
    private PlaytimeManager playtimeManager;
    private TotemManager totemManager;
    private DayManager dayManager;
    private HurricaneManager hurricaneManager;
    private DeathMessageManager deathMessageManager;
    private DiscordManager discordManager;
    private DementialWheelManager dementialWheelManager;
    private InactivityManager inactivityManager;

    // ==========================================================
    // LISTENERS
    // ==========================================================
    private TotemListener totemListener;

    @Override
    public void onEnable() {
        getLogger().info("Loading configuration...");
        instance = this;
        saveDefaultConfig();

        // 1. Instanciación de Managers
        getLogger().info("Loading managers...");
        this.languageManager = new LanguageManager(this);
        this.emoteManager = new EmoteManager(this);
        this.chatManager = new ChatManager(this);
        this.scoreboardManager = new ScoreboardManager(this, languageManager);
        this.afkManager = new AFKManager(this);
        this.playtimeManager = new PlaytimeManager(this);
        this.totemManager = new TotemManager(this);
        this.dayManager = new DayManager(this);
        this.hurricaneManager = new HurricaneManager(this, languageManager);
        this.deathMessageManager = new DeathMessageManager(this);
        this.discordManager = new pp.sheero.permapiola.discord.DiscordManager(this);
        this.dementialWheelManager = new pp.sheero.permapiola.dementialwheel.DementialWheelManager(this, languageManager);
        this.dementialWheelManager.loadData();
        this.inactivityManager = new InactivityManager(this);
        LuckPermsUtils.registerListeners(this);

        // 2. Registro de Listeners
        getLogger().info("Loading listeners...");
        registerListeners();

        TeamManager.loadConfigCache(this);

        // 3. Registro de Comandos (Antiguos Bukkit)
        getLogger().info("Loading commands...");

        // 3.5 Registro de Comandos Modernos (Brigadier)
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            GmCommand.register(event.registrar(), languageManager);
            GmaCommand.register(event.registrar(), languageManager);
            GmcCommand.register(event.registrar(), languageManager);
            GmsCommand.register(event.registrar(), languageManager);
            GmspCommand.register(event.registrar(), languageManager);
            MsgCommand.register(event.registrar(), languageManager, emoteManager);
            ReplyCommand.register(event.registrar(), languageManager, emoteManager);
            ChatCommand.register(event.registrar(), chatManager, languageManager);
            StaffChatCommand.register(event.registrar(), chatManager, languageManager, emoteManager);
            TeamChatCommand.register(event.registrar(), languageManager);
            TeamCommand.register(event.registrar(), this, languageManager);
            HelpOpCommand.register(event.registrar(), this, languageManager, emoteManager);
            BroadcastCommand.register(event.registrar(), languageManager, emoteManager);
            PermaPiolaCommand.register(event.registrar(), this, languageManager, emoteManager);
            EmotesCommand.register(event.registrar(), emoteManager, languageManager);
            EnderChestCommand.register(event.registrar(), languageManager);
            SidebarCommand.register(event.registrar(), scoreboardManager, languageManager);
            ReviveCommand.register(event.registrar(), this, languageManager);
            PlayerIpCommand.register(event.registrar(), languageManager);
            RenameCommand.register(event.registrar(), languageManager);
            InventoryCommand.register(event.registrar(), languageManager);
            DeathMessageCommand.register(event.registrar(), this, languageManager);
            DementialWheelCommand.register(event.registrar(), dementialWheelManager, languageManager);
            HurricaneCommand.register(event.registrar(), hurricaneManager, languageManager);
            SpectatorChatCommand.register(event.registrar(), chatManager, languageManager, emoteManager);
            TotemCommand.register(event.registrar(), totemManager, languageManager);
            PlaytimeCommand.register(event.registrar(), playtimeManager, languageManager);
        });

        // 4. Tareas, Dependencias (ProtocolLib) y Carga de Datos Estáticos
        new PlaytimeTask(this, afkManager, playtimeManager).runTaskTimer(this, 20L, 20L);

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            GlowManager.registerGlowPacketListener(this);
        } else {
            getLogger().warning("¡ProtocolLib no está instalado! El comando /team glow estará desactivado.");
        }

        TeamManager.loadData(this);
        DeathStateManager.loadData(this);
        DeathInventoryManager.init(this);

        getLogger().info("Successfully enabled.");
    }

    @Override
    public void onDisable() {
        // Guardado de Datos
        if (playtimeManager != null) playtimeManager.saveData();
        if (totemManager != null) totemManager.saveData();
        if (chatManager != null) chatManager.saveData();
        if (hurricaneManager != null) hurricaneManager.saveData();
        if (deathMessageManager != null) deathMessageManager.saveData();
        if (dementialWheelManager != null) dementialWheelManager.saveData();
        if (inactivityManager != null) inactivityManager.saveData();

        TeamManager.saveData(this);
        DeathStateManager.saveData(this);
        DeathInventoryManager.saveDataSync();
    }

    // ==========================================================
    // MÉTODOS DE REGISTRO
    // ==========================================================

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();

        // Instanciar Listeners Cacheados
        this.totemListener = new TotemListener(this, totemManager, languageManager);

        // Listeners Base y Módulos
        pm.registerEvents(this.afkManager, this);
        pm.registerEvents(this.scoreboardManager, this);
        pm.registerEvents(this.totemListener, this);
        pm.registerEvents(this.hurricaneManager, this);
        pm.registerEvents(new pp.sheero.permapiola.hurricane.HurricaneDeathListener(this, languageManager), this);

        // Listeners de Utilidad
        pm.registerEvents(new ChatListener(this, chatManager, languageManager, emoteManager), this);
        pm.registerEvents(new CombatLogListener(this, languageManager), this);
        pm.registerEvents(new PlayerDeathListener(), this);
        pm.registerEvents(new PlayerConnectionListener(this, languageManager), this);
        pm.registerEvents(new PlayerInteractListener(), this);
        pm.registerEvents(new ServerPingListener(this), this);
        pm.registerEvents(new MiningListener(this, languageManager), this);
        pm.registerEvents(new InventoryListener(this, languageManager), this);
        pm.registerEvents(new EmoteGUIListener(languageManager), this);
        pm.registerEvents(new CommandBlockerListener(this, languageManager), this);
        pm.registerEvents(new DeathInventoryEditListener(languageManager), this);
        pm.registerEvents(this.inactivityManager, this);

        // Demential Wheel
        pm.registerEvents(new DementialWheelListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, new DementialWheelTask(this), 10L, 10L);
    }

    // ==========================================================
    // GETTERS DE INSTANCIAS
    // ==========================================================

    public static PermaPiola getInstance() {
        return instance;
    }

    public LanguageManager getLanguageManager() { return languageManager; }
    public DayManager getDayManager() { return dayManager; }
    public HurricaneManager getHurricaneManager() { return hurricaneManager; }
    public DeathMessageManager getDeathMessageManager() { return deathMessageManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public DementialWheelManager getDementialWheelManager() { return dementialWheelManager; }
    public EmoteManager getEmoteManager() { return emoteManager; }
    public ChatManager getChatManager() { return chatManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public AFKManager getAfkManager() { return afkManager; }
    public PlaytimeManager getPlaytimeManager() { return playtimeManager; }
    public TotemManager getTotemManager() { return totemManager; }
    public InactivityManager getInactivityManager() { return inactivityManager; }

    public TotemListener getTotemListener() { return totemListener; }
}