package ru.Fronzter.MindAc.entity;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.scheduler.BukkitTask;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.service.AnalysisService;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Data
@RequiredArgsConstructor
public final class PlayerEntity {
    private final UUID uuid;
    private final String name;

    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;
    private final List<Frame> frames = new LinkedList<>();
    private List<Frame> lastAnalyzedFrames = null;

    private volatile boolean isProcessingFlag = false;
    private long combatTagUntil = 0L;
    private BukkitTask postCombatAnalysisTask = null;

    public void setLastAnalyzedFrames(List<Frame> frames) {
        this.lastAnalyzedFrames = new ArrayList<>(frames);
    }

    public boolean isInCombat() {
        return System.currentTimeMillis() < combatTagUntil;
    }

    public void tagCombat(long durationTicks) {
        this.combatTagUntil = System.currentTimeMillis() + (durationTicks * 50);

        if (postCombatAnalysisTask != null) {
            postCombatAnalysisTask.cancel();
        }

        postCombatAnalysisTask = MindAI.getInstance().getServer().getScheduler().runTaskLaterAsynchronously(
                MindAI.getInstance(),
                () -> {
                    int framesToAnalyze = MindAI.getInstance().getConfig().getInt("ml-check.frames-to-analyze", 150);
                    if (this.getFrames().size() >= framesToAnalyze) {
                        AnalysisService.analyze(this);
                    }
                    this.getFrames().clear();
                },
                durationTicks
        );
    }
}
