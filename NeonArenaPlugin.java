package com.cubetechnic.neonarena;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** CubeTechnic Neon arena generator. All structures are built only in CubeTechnic_Neon. */
public final class NeonArenaPlugin extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor {
    private static final String WORLD_NAME = "CubeTechnic_Neon";
    private static final int HALF = 22, HEIGHT = 18, SPACING = 3000;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Set<String> unlocked = new HashSet<>();
    private final Set<UUID> triggered = new HashSet<>();
    private final Random random = new Random();
    private BukkitTask generationTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("neonarena")).setExecutor(this);
        loadArenas();
        long interval = Math.max(5L, getConfig().getLong("generation-interval-minutes", 15L));
        generationTask = Bukkit.getScheduler().runTaskTimer(this, this::generateNearPlayers, 200L, interval * 60L * 20L);
        getLogger().info("NeonArena enabled. World target: " + WORLD_NAME);
    }

    @Override public void onDisable() {
        if (generationTask != null) generationTask.cancel();
        saveArenas();
    }

    private void generateNearPlayers() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;
        List<Player> players = new ArrayList<>();
        for (Player p : world.getPlayers()) if (p.isOnline()) players.add(p);
        if (players.isEmpty()) return;
        int attempts = Math.max(1, getConfig().getInt("arenas-per-cycle", 1));
        for (int i=0; i<attempts; i++) {
            Player near = players.get(random.nextInt(players.size()));
            int radius = Math.max(500, getConfig().getInt("spawn-radius", 5000));
            int x = near.getLocation().getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = near.getLocation().getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            x = Math.floorDiv(x, SPACING) * SPACING + 100;
            z = Math.floorDiv(z, SPACING) * SPACING + 100;
            String key = x + "," + z;
            if (arenas.containsKey(key) || tooCloseToExistingArena(x, z)) continue;
            buildArena(world, x, z);
        }
    }

    /** Enforces at least 3,000 blocks between arena centers. */
    private boolean tooCloseToExistingArena(int x, int z) {
        long minimumSquared = 3000L * 3000L;
        for (Arena arena : arenas.values()) {
            long dx = (long) x - arena.doorX;
            long dz = (long) z - arena.doorZ;
            if (dx * dx + dz * dz < minimumSquared) return true;
        }
        return false;
    }

    private void buildArena(World w, int cx, int cz) {
        if (tooCloseToExistingArena(cx, cz)) {
            getLogger().warning("Arena skipped: less than 3000 blocks from an existing arena at " + cx + ", " + cz);
            return;
        }
        // Surface placement: calculate terrain height at the center and build upward, never underground.
        int y = w.getHighestBlockYAt(cx, cz) + 2;
        y = Math.max(w.getMinHeight() + 5, Math.min(y, w.getMaxHeight() - HEIGHT - 4));
        int minX=cx-HALF, maxX=cx+HALF, minZ=cz-HALF, maxZ=cz+HALF;
        Material black=Material.BLACK_CONCRETE, red=Material.RED_CONCRETE, blue=Material.BLUE_CONCRETE;
        Material floor=Material.POLISHED_BLACKSTONE, trim=Material.REDSTONE_BLOCK;
        // Floor and roof, with a raised, angular neon frame.
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            set(w,x,y,z,floor);
            set(w,x,y+HEIGHT,z,black);
            if ((x-minX)%6==0 && (z-minZ)%6==0) set(w,x,y+HEIGHT,z,blue);
        }
        // Four fully enclosed walls; front wall leaves one 3-wide x 4-high entrance.
        int doorX=cx, doorZ=minZ;
        for(int yy=1;yy<HEIGHT;yy++) {
            for(int x=minX;x<=maxX;x++) {
                boolean entrance = (Math.abs(x-doorX)<=1 && yy<=4);
                if (!entrance) set(w,x,y+yy,minZ, ((x+yy)%7==0)?red:black);
                set(w,x,y+yy,maxZ, ((x+yy)%9==0)?blue:black);
            }
            for(int z=minZ;z<=maxZ;z++) {
                set(w,minX,y+yy,z, ((z+yy)%8==0)?blue:black);
                set(w,maxX,y+yy,z, ((z+yy)%8==0)?red:black);
            }
        }
        // Monumental frame around the entry, glowing red/blue contrast.
        for(int yy=1;yy<=6;yy++) { set(w,doorX-2,y+yy,doorZ,red); set(w,doorX+2,y+yy,doorZ,blue); }
        for(int dx=-2;dx<=2;dx++) set(w,doorX+dx,y+6,doorZ,dx%2==0?blue:red);
        // Inner chamber decoration: a raised central platform, no boss spawned.
        for(int x=cx-5;x<=cx+5;x++) for(int z=cz-5;z<=cz+5;z++) {
            if(Math.abs(x-cx)==5 || Math.abs(z-cz)==5) set(w,x,y+1,z, (x+z)%2==0?red:blue);
            else set(w,x,y+1,z,black);
        }
        // Neon pillars in the corners.
        for(int[] p:new int[][]{{minX+3,minZ+3},{maxX-3,minZ+3},{minX+3,maxZ-3},{maxX-3,maxZ-3}})
            for(int yy=1;yy<HEIGHT-1;yy++) set(w,p[0],y+yy,p[1],yy%3==0?blue:black);
        Arena a=new Arena(w.getName(),cx,y,cz,minX,maxX,minZ,maxZ,doorX,doorZ);
        arenas.put(cx+","+cz,a);
        saveArenas();
        getLogger().info("Generated protected Neon arena at " + cx + ", " + y + ", " + cz);
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (e.getTo()==null || (e.getFrom().getBlockX()==e.getTo().getBlockX() && e.getFrom().getBlockY()==e.getTo().getBlockY() && e.getFrom().getBlockZ()==e.getTo().getBlockZ())) return;
        Player p=e.getPlayer();
        if (!WORLD_NAME.equals(p.getWorld().getName())) return;
        for(Arena a:arenas.values()) {
            if(!a.world.equals(p.getWorld().getName())) continue;
            // Player crossed the entrance from outside to the interior; close it behind them.
            Location to = e.getTo();
            boolean inside = to.getBlockX()>a.minX && to.getBlockX()<a.maxX && to.getBlockZ()>a.minZ && to.getBlockZ()<a.maxZ && to.getBlockY()>=a.y && to.getBlockY()<a.y+HEIGHT;
            boolean enteredThroughDoor = Math.abs(to.getBlockX()-a.doorX)<=2 && to.getBlockZ()>=a.doorZ && to.getBlockZ()<=a.doorZ+3 && to.getBlockY()>=a.y+1 && to.getBlockY()<=a.y+5;
            if(inside && enteredThroughDoor && !triggered.contains(p.getUniqueId())) {
                triggered.add(p.getUniqueId());
                closeEntrance(a);
                // Call existing ORBITFX command as the entering player, so only their client gets the show/music.
                Bukkit.getScheduler().runTask(this, () -> {
                    org.bukkit.permissions.PermissionAttachment attachment = p.addAttachment(this, "orbitfx.admin", true);
                    try { p.performCommand("orbitfx start"); } finally { p.removeAttachment(attachment); }
                });
                p.sendTitle(ChatColor.DARK_RED + "Corrupt501", ChatColor.WHITE + "¡¿POR QUÉ ENTRASTES!?", 10, 60, 10);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline()) p.sendTitle(ChatColor.DARK_RED + "Corrupt501", ChatColor.RED + "¡NO te llevarás el Emblema de la Gravedad!", 10, 60, 10);
                }, 80L);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (p.isOnline()) p.sendTitle(ChatColor.DARK_RED + "Corrupt501", ChatColor.WHITE + "Prepárate...", 10, 60, 10);
                }, 140L);
                return;
            }
        }
    }

    private void closeEntrance(Arena a) {
        World w=Bukkit.getWorld(a.world); if(w==null) return;
        for(int yy=1;yy<=4;yy++) for(int dx=-1;dx<=1;dx++) set(w,a.doorX+dx,a.y+yy,a.doorZ, yy==1 || yy==4 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE);
        // Blue and red locking bars on the inside face.
        set(w,a.doorX-1,a.y+2,a.doorZ+1,Material.BLUE_CONCRETE);
        set(w,a.doorX+1,a.y+2,a.doorZ+1,Material.RED_CONCRETE);
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { protect(e.getBlock(), e.getPlayer(), e); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { protect(e.getBlock(), e.getPlayer(), e); }
    private void protect(Block b, Player p, org.bukkit.event.Cancellable event) {
        if(!WORLD_NAME.equals(b.getWorld().getName())) return;
        Arena arena = findArena(b.getWorld().getName(),b.getX(),b.getY(),b.getZ());
        if (arena == null) return;
        if(p.hasPermission("neonarena.admin") && unlocked.contains(arenaKey(arena))) return;
        event.setCancelled(true); p.sendMessage(ChatColor.RED+"Este estadio está protegido contra modificaciones.");
    }
    @EventHandler public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> findArena(b.getWorld().getName(),b.getX(),b.getY(),b.getZ())!=null); }
    @EventHandler public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> findArena(b.getWorld().getName(),b.getX(),b.getY(),b.getZ())!=null); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { triggered.remove(e.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(args.length==0) { sender.sendMessage("/neonarena list | generate | unlock <x> <z> | lock <x> <z>"); return true; }
        if(!sender.hasPermission("neonarena.admin")) { sender.sendMessage(ChatColor.RED+"No tienes permiso."); return true; }
        if(args[0].equalsIgnoreCase("generate")) {
            if(!(sender instanceof Player p) || !WORLD_NAME.equals(p.getWorld().getName())) { sender.sendMessage("Ejecuta este comando como jugador dentro de CubeTechnic_Neon."); return true; }
            int cx = p.getLocation().getBlockX() + 80, cz = p.getLocation().getBlockZ() + 80;
            int tries = 0;
            while (tooCloseToExistingArena(cx, cz) && tries < 100) {
                cx += 3000;
                tries++;
            }
            if (tooCloseToExistingArena(cx, cz)) { sender.sendMessage(ChatColor.RED + "No se encontró un lugar separado 3000 bloques de otros estadios."); return true; }
            buildArena(p.getWorld(), cx, cz); sender.sendMessage(ChatColor.GREEN+"Estadio generado en la superficie de Neon, separado al menos 3000 bloques de los demás."); return true;
        }
        if(args[0].equalsIgnoreCase("list")) { sender.sendMessage(ChatColor.AQUA+"Estadios Neon: "+arenas.size()); for(Arena a:arenas.values()) sender.sendMessage("- "+a.doorX+", "+a.y+", "+a.doorZ); return true; }
        if((args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("lock")) && args.length>=3) {
            try { int x=Integer.parseInt(args[1]), z=Integer.parseInt(args[2]); Arena target=null; for(Arena a:arenas.values()) if(x>=a.minX&&x<=a.maxX&&z>=a.minZ&&z<=a.maxZ) { target=a; break; } if(target==null) { sender.sendMessage(ChatColor.RED+"No hay estadio registrado en esas coordenadas X/Z."); return true; } String k=arenaKey(target); if(args[0].equalsIgnoreCase("unlock")) unlocked.add(k); else unlocked.remove(k); saveArenas(); sender.sendMessage(ChatColor.GREEN+(args[0].equalsIgnoreCase("unlock")?"Estadio desbloqueado para editar.":"Estadio protegido.")); }
            catch(NumberFormatException ex) { sender.sendMessage("X y Z deben ser números."); } return true;
        }
        sender.sendMessage("/neonarena list | generate | unlock <x> <z> | lock <x> <z>"); return true;
    }

    private Arena findArena(String world,int x,int y,int z) { for(Arena a:arenas.values()) if(a.world.equals(world)&&x>=a.minX&&x<=a.maxX&&z>=a.minZ&&z<=a.maxZ&&y>=a.y&&y<=a.y+HEIGHT) return a; return null; }
    private String arenaKey(Arena a) { return a.world+":"+a.doorX+":"+a.doorZ; }
    private void set(World w,int x,int y,int z,Material m) { Block b=w.getBlockAt(x,y,z); if(b.getType()!=m) b.setType(m,false); }
    private void loadArenas() {
        for(String s:getConfig().getStringList("arenas")) { String[] v=s.split(","); if(v.length!=10) continue; try { Arena a=new Arena(v[0],Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]),Integer.parseInt(v[4]),Integer.parseInt(v[5]),Integer.parseInt(v[6]),Integer.parseInt(v[7]),Integer.parseInt(v[8]),Integer.parseInt(v[9])); arenas.put(a.doorX+","+a.doorZ,a); } catch(Exception ignored){} }
        unlocked.addAll(getConfig().getStringList("unlocked"));
    }
    private void saveArenas() { List<String> out=new ArrayList<>(); for(Arena a:arenas.values()) out.add(String.join(",",a.world,""+a.doorX,""+a.y,""+a.doorZ,""+a.minX,""+a.maxX,""+a.minZ,""+a.maxZ,""+a.doorX,""+a.doorZ)); getConfig().set("arenas",out); getConfig().set("unlocked",new ArrayList<>(unlocked)); saveConfig(); }
    private static final class Arena { final String world; final int y,doorX,doorZ,minX,maxX,minZ,maxZ; Arena(String world,int x,int y,int z,int minX,int maxX,int minZ,int maxZ,int doorX,int doorZ){this.world=world;this.doorX=x;this.y=y;this.doorZ=z;this.minX=minX;this.maxX=maxX;this.minZ=minZ;this.maxZ=maxZ;} }
}
