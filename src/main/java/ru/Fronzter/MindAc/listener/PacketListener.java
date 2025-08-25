package ru.Fronzter.MindAc.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.Frame;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;
import ru.Fronzter.MindAc.service.AnalysisService;
import java.util.List;

public class PacketListener extends PacketListenerAbstract {

    private final boolean isMlCheckEnabled;
    private final int framesToAnalyze;

    public PacketListener() {
        this.isMlCheckEnabled = MindAI.getInstance().getConfig().getBoolean("ml-check.enabled", false);
        this.framesToAnalyze = MindAI.getInstance().getConfig().getInt("ml-check.frames-to-analyze", 150);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!isMlCheckEnabled) return;

        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;

        PlayerEntity entity = PlayerRegistry.getPlayer(user.getUUID());
        if (entity == null) return;

        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);

            if (flying.hasRotationChanged()) {
                Frame frame = new Frame(
                        flying.getLocation().getYaw() - entity.getLastYaw(),
                        flying.getLocation().getPitch() - entity.getLastPitch()
                );

                List<Frame> frames = entity.getFrames();
                frames.add(frame);

                while (frames.size() > framesToAnalyze) {
                    frames.remove(0);
                }
            }
            updateLastLocation(entity, flying.getLocation());
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
            if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {

                Player bukkitPlayer = Bukkit.getPlayer(user.getUUID());
                if (bukkitPlayer != null) {
                    Entity target = bukkitPlayer.getWorld().getEntities().stream()
                            .filter(e -> e.getEntityId() == interact.getEntityId())
                            .findFirst().orElse(null);

                    if (target instanceof Player) {
                        if (entity.getFrames().size() >= framesToAnalyze) {
                            AnalysisService.analyze(entity);
                            entity.getFrames().clear();
                        }
                    }
                }
            }
        }
    }

    private void updateLastLocation(PlayerEntity entity, com.github.retrooper.packetevents.protocol.world.Location location) {
        entity.setLastYaw(location.getYaw());
        entity.setLastPitch(location.getPitch());
    }
}