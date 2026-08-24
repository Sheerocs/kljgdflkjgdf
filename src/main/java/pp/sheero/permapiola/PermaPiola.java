package pp.sheero.permapiola;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import pp.sheero.permapiola.hurricane.*;
import pp.sheero.permapiola.managers.*;
import pp.sheero.permapiola.playtime.*;
import pp.sheero.permapiola.teams.*;
import pp.sheero.permapiola.totem.*;
import pp.sheero.permapiola.utilidad.commands.*;
import pp.sheero.permapiola.utilidad.listeners.*;

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
    private pp.sheero.permapiola.discord.DiscordManager discordManager;
    private pp.sheero.permapiola.dementialwheel.DementialWheelManager dementialWheelManager;
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
        pp.sheero.permapiola.utils.LuckPermsUtils.registerListeners(this);

        // 2. Registro de Listeners
        getLogger().info("Loading listeners...");
        registerListeners();

        TeamManager.loadConfigCache(this);

        // 3. Registro de Comandos (Antiguos Bukkit)
        getLogger().info("Loading commands...");
        registerCommands();

        // 3.5 Registro de Comandos Modernos (Brigadier)
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            pp.sheero.permapiola.utilidad.commands.GmCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.GmaCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.GmcCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.GmsCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.GmspCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.MsgCommand.register(event.registrar(), languageManager, emoteManager);
            pp.sheero.permapiola.utilidad.commands.ReplyCommand.register(event.registrar(), languageManager, emoteManager);
            pp.sheero.permapiola.utilidad.commands.ChatCommand.register(event.registrar(), chatManager, languageManager);
            pp.sheero.permapiola.utilidad.commands.StaffChatCommand.register(event.registrar(), chatManager, languageManager, emoteManager);
            pp.sheero.permapiola.teams.TeamChatCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.HelpOpCommand.register(event.registrar(), this, languageManager, emoteManager);
            pp.sheero.permapiola.utilidad.commands.BroadcastCommand.register(event.registrar(), languageManager, emoteManager);
            pp.sheero.permapiola.utilidad.commands.PermaPiolaCommand.register(event.registrar(), this, languageManager, emoteManager);
            pp.sheero.permapiola.utilidad.commands.EmotesCommand.register(event.registrar(), emoteManager, languageManager);
            pp.sheero.permapiola.utilidad.commands.EnderChestCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.SidebarCommand.register(event.registrar(), scoreboardManager, languageManager);
            pp.sheero.permapiola.utilidad.commands.ReviveCommand.register(event.registrar(), this, languageManager);
            pp.sheero.permapiola.utilidad.commands.PlayerIpCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.RenameCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.utilidad.commands.InventoryCommand.register(event.registrar(), languageManager);
            pp.sheero.permapiola.hurricane.DeathMessageCommand.register(event.registrar(), this, languageManager);
            pp.sheero.permapiola.dementialwheel.DementialWheelCommand.register(event.registrar(), dementialWheelManager, languageManager);
            pp.sheero.permapiola.hurricane.HurricaneCommand.register(event.registrar(), hurricaneManager, languageManager);
            // GmcCommand.register(...)
            // GmsCommand.register(...)
            // PlaytimeCommand.register(...)
        });

        // 4. Tareas, Dependencias (ProtocolLib) y Carga de Datos Estáticos
        new PlaytimeTask(this, afkManager, playtimeManager).runTaskTimer(this, 20L, 20L);

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            GlowManager.registerGlowPacketListener(this);
        } else {
            getLogger().warning("¡ProtocolLib no está instalado! El comando /team glow estará desactivado.");
        }

        TeamManager.loadData(this);
        pp.sheero.permapiola.utils.DeathStateManager.loadData(this);
        pp.sheero.permapiola.utils.DeathInventoryManager.init(this);

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
        pp.sheero.permapiola.utils.DeathStateManager.saveData(this);
        pp.sheero.permapiola.utils.DeathInventoryManager.saveDataSync();
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
        pm.registerEvents(new EmoteGUIListener(), this);
        pm.registerEvents(new CommandBlockerListener(this, languageManager), this);
        pm.registerEvents(new DeathInventoryEditListener(languageManager), this);
        pm.registerEvents(this.inactivityManager, this);

        // Demential Wheel
        pm.registerEvents(new pp.sheero.permapiola.dementialwheel.DementialWheelListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, new pp.sheero.permapiola.dementialwheel.DementialWheelTask(this), 10L, 10L);
    }

    private void registerCommands() {
        // Teams
        getCommand("team").setExecutor(new TeamCommand(this, languageManager));
        getCommand("team").setTabCompleter(new TeamCommand(this, languageManager));

        // Playtime
        getCommand("playtime").setExecutor(new PlaytimeCommand(this, playtimeManager, languageManager));
        getCommand("playtime").setTabCompleter(new PlaytimeCommand(this, playtimeManager, languageManager));

        // Totems
        getCommand("totem").setExecutor(new TotemCommand(this, totemManager, languageManager));
        getCommand("totem").setTabCompleter(new TotemCommand(this, totemManager, languageManager));
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
    public pp.sheero.permapiola.discord.DiscordManager getDiscordManager() { return discordManager; }
    public pp.sheero.permapiola.dementialwheel.DementialWheelManager getDementialWheelManager() { return dementialWheelManager; }
    public EmoteManager getEmoteManager() { return emoteManager; }
    public ChatManager getChatManager() { return chatManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public AFKManager getAfkManager() { return afkManager; }
    public PlaytimeManager getPlaytimeManager() { return playtimeManager; }
    public TotemManager getTotemManager() { return totemManager; }
    public InactivityManager getInactivityManager() { return inactivityManager; }

    public TotemListener getTotemListener() { return totemListener; }
}